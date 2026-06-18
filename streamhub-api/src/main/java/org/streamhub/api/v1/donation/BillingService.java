package org.streamhub.api.v1.donation;

import java.time.Duration;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.v1.actionlog.ActionLogPublisher;
import org.streamhub.api.v1.donation.entity.Donation;
import org.streamhub.api.v1.donation.entity.DonationStatus;
import org.streamhub.api.v1.donation.entity.DonationType;
import org.streamhub.api.v1.donation.entity.Subscription;
import org.streamhub.api.v1.donation.entity.SubscriptionPlan;
import org.streamhub.api.v1.donation.entity.SubscriptionStatus;
import org.streamhub.api.v1.donation.repository.DonationRepository;
import org.streamhub.api.v1.donation.repository.SubscriptionPlanRepository;
import org.streamhub.api.v1.donation.repository.SubscriptionRepository;

/**
 * Per-cycle billing transaction boundary called by {@link BillingScheduler}. Each cycle charge —
 * donation record, point accrual, and subscription advance — commits atomically so a single
 * failed subscription cannot poison the whole batch. Test mode only; no real PG is invoked.
 *
 * <p><b>Idempotency.</b> Each cycle charge inserts a {@link Donation} keyed by
 * {@code (subscription_id, cycle_no)}, guarded by the {@code uk_donation_cycle} unique constraint.
 * If the same cycle is charged twice — a scheduler double-fire, or a retry that runs after the
 * cycle committed but before {@link Subscription#advanceCycle} persisted — the second insert
 * collides on the constraint and is skipped, so no cycle is ever billed twice.
 *
 * <p><b>Failure policy.</b> A genuine charge failure rolls back the cycle transaction and is
 * reported back to the scheduler, which calls {@link #recordFailure} in an independent
 * transaction. That method persists a {@link DonationStatus#FAILED} attempt record and applies a
 * bounded exponential backoff via {@link Subscription#recordBillingFailure}: each retry is spaced
 * further out instead of re-firing every 5 minutes, and after {@code app.billing.max-attempts}
 * consecutive failures the subscription is auto-PAUSED so it leaves the due-billing scan entirely
 * and becomes visible to an operator. This is what prevents an unpayable subscription from
 * pinning {@code next_billing_at} in the past and looping forever.
 */
@Slf4j
@Service
public class BillingService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final DonationRepository donationRepository;
    private final PointLedgerWriter pointLedgerWriter;
    private final ActionLogPublisher actionLogPublisher;

    /** Maximum consecutive billing failures before a subscription is auto-paused. */
    @Value("${app.billing.max-attempts:3}")
    private int maxAttempts;

    /** Backoff after the first failure (doubles each subsequent attempt). */
    @Value("${app.billing.backoff-base-minutes:60}")
    private long backoffBaseMinutes;

    /** Ceiling for the exponential backoff between retries. */
    @Value("${app.billing.backoff-max-hours:24}")
    private long backoffMaxHours;

    public BillingService(
            SubscriptionRepository subscriptionRepository,
            SubscriptionPlanRepository planRepository,
            DonationRepository donationRepository,
            PointLedgerWriter pointLedgerWriter,
            ActionLogPublisher actionLogPublisher) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.donationRepository = donationRepository;
        this.pointLedgerWriter = pointLedgerWriter;
        this.actionLogPublisher = actionLogPublisher;
    }

    /**
     * Charges a single billing cycle for the given subscription and advances its next billing date.
     * Re-reads the subscription inside the transaction and bails out if it is no longer ACTIVE
     * (defends against a pause/cancel that landed between the scan and this charge).
     *
     * <p>The cycle charge is idempotent: a duplicate insert for an already-billed
     * {@code (subscription_id, cycle_no)} is caught and skipped rather than billed twice. A real
     * charge failure (anything other than the idempotency collision) propagates so the scheduler
     * can record it via {@link #recordFailure}.
     *
     * @param subscriptionId the subscription to charge
     * @param now            the scan time used as the charge timestamp and next-cycle anchor
     */
    @Transactional
    public void chargeOneCycle(Long subscriptionId, LocalDateTime now) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            return;
        }
        SubscriptionPlan plan = planRepository.findById(subscription.getPlanId())
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));

        long point = plan.getPrice() * plan.getPointRate() / 100;
        int cycleNo = subscription.getCycleNo() + 1;

        Donation donation;
        try {
            donation = donationRepository.saveAndFlush(Donation.builder()
                    .memberId(subscription.getMemberId())
                    .subscriptionId(subscription.getId())
                    .type(DonationType.SUBSCRIPTION)
                    .amount(plan.getPrice())
                    .cycleNo(cycleNo)
                    .status(DonationStatus.PAID)
                    .pointAwarded(point)
                    .testMode("Y")
                    .paidAt(now)
                    .build());
        } catch (DataIntegrityViolationException e) {
            // Idempotency guard: this (subscription_id, cycle_no) was already charged
            // (scheduler double-fire or retry after a prior commit). Skip silently.
            log.info("Cycle {} for subscription {} already charged, skipping duplicate",
                    cycleNo, subscription.getId());
            return;
        }

        pointLedgerWriter.append(subscription.getMemberId(), point,
                "정기후원 " + cycleNo + "회차 적립", donation.getId());

        subscription.advanceCycle(now.plusMonths(plan.getPeriodMonths()));
        subscriptionRepository.saveAndFlush(subscription);

        actionLogPublisher.publish("BILLING_CHARGE", "SUBSCRIPTION",
                String.valueOf(subscription.getId()), "회차 " + cycleNo + " / ₩" + plan.getPrice());
    }

    /**
     * Records a failed billing attempt in its own transaction so the failure outcome survives the
     * rollback of the cycle charge. Persists a {@link DonationStatus#FAILED} donation record for
     * audit, then applies the bounded-retry / auto-pause policy on the subscription (see
     * {@link Subscription#recordBillingFailure}). Runs as {@code REQUIRES_NEW} because the caller
     * reaches it from the {@code catch} block of a rolled-back cycle transaction.
     *
     * @param subscriptionId the subscription whose charge failed
     * @param now            the failure timestamp
     * @param reason         short human-readable failure reason for the action log
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long subscriptionId, LocalDateTime now, String reason) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId).orElse(null);
        if (subscription == null || subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            return;
        }
        int cycleNo = subscription.getCycleNo() + 1;
        long amount = planRepository.findById(subscription.getPlanId())
                .map(SubscriptionPlan::getPrice)
                .orElse(0L);

        donationRepository.save(Donation.builder()
                .memberId(subscription.getMemberId())
                .subscriptionId(subscription.getId())
                .type(DonationType.SUBSCRIPTION)
                .amount(amount)
                .cycleNo(null)
                .status(DonationStatus.FAILED)
                .pointAwarded(0L)
                .testMode("Y")
                .paidAt(now)
                .build());

        subscription.recordBillingFailure(now, maxAttempts,
                Duration.ofMinutes(backoffBaseMinutes), Duration.ofHours(backoffMaxHours));
        subscriptionRepository.saveAndFlush(subscription);

        if (subscription.getStatus() == SubscriptionStatus.PAUSED) {
            log.warn("Subscription {} auto-paused after {} consecutive billing failures",
                    subscription.getId(), subscription.getFailedAttempts());
            actionLogPublisher.publish("BILLING_AUTO_PAUSE", "SUBSCRIPTION",
                    String.valueOf(subscription.getId()),
                    "결제 " + subscription.getFailedAttempts() + "회 연속 실패로 일시정지");
        } else {
            actionLogPublisher.publish("BILLING_FAILED", "SUBSCRIPTION",
                    String.valueOf(subscription.getId()),
                    "회차 " + cycleNo + " 실패(" + subscription.getFailedAttempts() + "회): " + reason);
        }
    }
}
