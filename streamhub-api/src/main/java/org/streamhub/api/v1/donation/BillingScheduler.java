package org.streamhub.api.v1.donation;

import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.streamhub.api.v1.donation.entity.Subscription;
import org.streamhub.api.v1.donation.entity.SubscriptionStatus;
import org.streamhub.api.v1.donation.repository.SubscriptionRepository;

/**
 * Recurring-billing simulator (★ core demo feature). On each tick it scans ACTIVE subscriptions
 * whose {@code next_billing_at} is due and charges them one cycle each, in an independent
 * transaction per subscription so one failure never blocks the rest of the batch. No real PG
 * integration — every charge is test mode.
 */
@Slf4j
@Component
public class BillingScheduler {

    private final SubscriptionRepository subscriptionRepository;
    private final BillingService billingService;

    public BillingScheduler(SubscriptionRepository subscriptionRepository,
                            BillingService billingService) {
        this.subscriptionRepository = subscriptionRepository;
        this.billingService = billingService;
    }

    /** Demo cadence: scan every 5 minutes (overridable via {@code app.billing.cron}). */
    @Scheduled(cron = "${app.billing.cron:0 */5 * * * *}")
    public void runDueBilling() {
        runDueBillingNow();
    }

    /**
     * Runs the due-billing scan immediately and returns the number of subscriptions charged.
     * Reused by the manual demo trigger ({@code POST /v1/donation/run-billing}).
     */
    public int runDueBillingNow() {
        LocalDateTime now = LocalDateTime.now();
        List<Subscription> due =
                subscriptionRepository.findByStatusAndNextBillingAtBefore(SubscriptionStatus.ACTIVE, now);
        if (due.isEmpty()) {
            return 0;
        }
        int processed = 0;
        for (Subscription subscription : due) {
            try {
                billingService.chargeOneCycle(subscription.getId(), now);
                processed++;
            } catch (RuntimeException e) {
                // The cycle transaction rolled back. Record the failure in its own transaction so
                // the FAILED record + backoff/auto-pause persist and the subscription does not keep
                // re-firing on the next 5-minute scan.
                log.warn("Billing failed for subscription {}: {}", subscription.getId(), e.getMessage());
                try {
                    billingService.recordFailure(subscription.getId(), now, e.getMessage());
                } catch (RuntimeException recordError) {
                    log.error("Failed to record billing failure for subscription {}: {}",
                            subscription.getId(), recordError.getMessage());
                }
            }
        }
        log.info("Billing run: {} subscriptions charged", processed);
        return processed;
    }
}
