package org.streamhub.api.v1.donation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A recurring-donation subscription. The entity owns its own state-machine transitions;
 * illegal transitions throw {@link IllegalStateException} for the service to convert.
 */
@Entity
@Table(name = "SUBSCRIPTION", indexes = {
        @Index(name = "idx_subscription_member", columnList = "member_id"),
        @Index(name = "idx_subscription_status", columnList = "status"),
        @Index(name = "idx_subscription_next_billing", columnList = "status, next_billing_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → MEMBER. */
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    /** FK → SUBSCRIPTION_PLAN. */
    @Column(name = "plan_id", nullable = false)
    private Long planId;

    /** Masked demo billing key, e.g. {@code bk_****1234}. */
    @Column(name = "billing_key_masked", nullable = false, length = 40)
    private String billingKeyMasked;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SubscriptionStatus status;

    /** Cycles billed so far (starts at 0). */
    @Column(name = "cycle_no", nullable = false)
    private Integer cycleNo;

    /**
     * Consecutive billing failures since the last successful charge (starts at 0). Reset to 0 on a
     * successful {@link #advanceCycle}. Drives the retry backoff and the auto-pause threshold.
     */
    @Column(name = "failed_attempts", nullable = false)
    private Integer failedAttempts;

    /** Timestamp of the most recent billing failure; {@code null} until one occurs. */
    @Column(name = "last_failure_at")
    private LocalDateTime lastFailureAt;

    /** Next scheduled billing time; {@code null} when PAUSED or CANCELED. */
    @Column(name = "next_billing_at")
    private LocalDateTime nextBillingAt;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private Subscription(Long memberId, Long planId, String billingKeyMasked,
                         SubscriptionStatus status, Integer cycleNo, LocalDateTime nextBillingAt,
                         LocalDateTime startedAt, LocalDateTime canceledAt,
                         LocalDateTime createdAt) {
        this.memberId = memberId;
        this.planId = planId;
        this.billingKeyMasked = billingKeyMasked;
        this.status = status;
        this.cycleNo = cycleNo != null ? cycleNo : 0;
        this.failedAttempts = 0;
        this.nextBillingAt = nextBillingAt;
        this.startedAt = startedAt != null ? startedAt : LocalDateTime.now();
        this.canceledAt = canceledAt;
        this.createdAt = createdAt != null ? createdAt : this.startedAt;
        this.updatedAt = this.createdAt;
    }

    /** Pauses an ACTIVE subscription and clears the next billing date. */
    public void pause() {
        if (this.status != SubscriptionStatus.ACTIVE) {
            throw new IllegalStateException("only an active subscription can be paused");
        }
        this.status = SubscriptionStatus.PAUSED;
        this.nextBillingAt = null;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Resumes a PAUSED subscription, scheduling the next billing date. Clears any accumulated
     * failure state so an operator-driven resume (including a resume after an auto-pause from
     * repeated billing failures) starts the retry budget fresh.
     */
    public void resume(LocalDateTime nextBillingAt) {
        if (this.status != SubscriptionStatus.PAUSED) {
            throw new IllegalStateException("only a paused subscription can be resumed");
        }
        this.status = SubscriptionStatus.ACTIVE;
        this.nextBillingAt = nextBillingAt;
        this.failedAttempts = 0;
        this.lastFailureAt = null;
        this.updatedAt = LocalDateTime.now();
    }

    /** Cancels the subscription (terminal). */
    public void cancel() {
        if (this.status == SubscriptionStatus.CANCELED) {
            throw new IllegalStateException("subscription is already canceled");
        }
        this.status = SubscriptionStatus.CANCELED;
        this.nextBillingAt = null;
        this.canceledAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Advances one billing cycle and schedules the next billing date (CRON only). Clears the
     * consecutive-failure counter — a success ends any in-progress retry sequence.
     */
    public void advanceCycle(LocalDateTime next) {
        this.cycleNo += 1;
        this.nextBillingAt = next;
        this.failedAttempts = 0;
        this.lastFailureAt = null;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Records a failed billing attempt for the current cycle and decides how the scheduler should
     * proceed, implementing a bounded retry with exponential backoff so a persistently failing
     * subscription never produces an infinite hot loop.
     *
     * <p>Policy:
     * <ul>
     *   <li>Increments {@code failedAttempts} and stamps {@code lastFailureAt}.</li>
     *   <li>If the attempt count is still below {@code maxAttempts}, pushes {@code nextBillingAt}
     *       forward by {@code baseBackoff × 2^(attempt-1)} (capped at {@code maxBackoff}). Because
     *       the next due time is now in the future, the 5-minute scan no longer re-fires it
     *       immediately — the retry is spaced out, not busy-looped.</li>
     *   <li>Once {@code maxAttempts} is reached, transitions the subscription to {@code PAUSED} and
     *       clears {@code nextBillingAt}. This permanently removes it from the due-billing scan
     *       (stopping the hot loop) and surfaces a PAUSED state an operator can see and act on
     *       (e.g. fix the billing key and resume).</li>
     * </ul>
     *
     * @param now         the failure timestamp / current scan time
     * @param maxAttempts maximum consecutive failures before auto-pausing (must be &gt;= 1)
     * @param baseBackoff backoff applied after the first failure; doubles each subsequent attempt
     * @param maxBackoff  ceiling for the computed backoff
     */
    public void recordBillingFailure(LocalDateTime now, int maxAttempts,
                                     Duration baseBackoff, Duration maxBackoff) {
        this.failedAttempts += 1;
        this.lastFailureAt = now;
        if (this.failedAttempts >= maxAttempts) {
            this.status = SubscriptionStatus.PAUSED;
            this.nextBillingAt = null;
        } else {
            long multiplier = 1L << (this.failedAttempts - 1);
            Duration backoff = baseBackoff.multipliedBy(multiplier);
            if (backoff.compareTo(maxBackoff) > 0) {
                backoff = maxBackoff;
            }
            this.nextBillingAt = now.plus(backoff);
        }
        this.updatedAt = LocalDateTime.now();
    }
}
