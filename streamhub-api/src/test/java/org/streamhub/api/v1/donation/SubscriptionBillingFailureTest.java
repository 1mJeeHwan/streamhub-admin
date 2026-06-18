package org.streamhub.api.v1.donation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.streamhub.api.v1.donation.entity.Subscription;
import org.streamhub.api.v1.donation.entity.SubscriptionStatus;

/**
 * Pure-logic unit tests for the billing-failure state machine on {@link Subscription}
 * ({@link Subscription#recordBillingFailure} and the failure reset on
 * {@link Subscription#advanceCycle}). Covers the bounded retry / exponential backoff that stops the
 * 5-minute hot loop and the auto-pause once the attempt budget is exhausted.
 */
class SubscriptionBillingFailureTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 18, 12, 0);
    private static final Duration BASE = Duration.ofMinutes(60);
    private static final Duration MAX = Duration.ofHours(24);

    private Subscription activeSubscription() {
        return Subscription.builder()
                .memberId(1L)
                .planId(1L)
                .billingKeyMasked("bk_****1234")
                .status(SubscriptionStatus.ACTIVE)
                .cycleNo(0)
                .nextBillingAt(NOW)
                .startedAt(NOW)
                .createdAt(NOW)
                .build();
    }

    @Test
    void firstFailure_schedulesBaseBackoffAndStaysActive() {
        Subscription sub = activeSubscription();

        sub.recordBillingFailure(NOW, 3, BASE, MAX);

        assertThat(sub.getFailedAttempts()).isEqualTo(1);
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(sub.getNextBillingAt()).isEqualTo(NOW.plusHours(1));
        assertThat(sub.getLastFailureAt()).isEqualTo(NOW);
    }

    @Test
    void secondFailure_doublesBackoff() {
        Subscription sub = activeSubscription();

        sub.recordBillingFailure(NOW, 3, BASE, MAX);
        sub.recordBillingFailure(NOW.plusHours(1), 3, BASE, MAX);

        assertThat(sub.getFailedAttempts()).isEqualTo(2);
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        // 60min * 2^(2-1) = 120min after the second failure timestamp.
        assertThat(sub.getNextBillingAt()).isEqualTo(NOW.plusHours(1).plusHours(2));
    }

    @Test
    void backoffIsCappedAtMax() {
        Subscription sub = activeSubscription();
        // maxAttempts high enough that we never auto-pause while probing the cap.
        sub.recordBillingFailure(NOW, 100, BASE, MAX); // 1h
        sub.recordBillingFailure(NOW, 100, BASE, MAX); // 2h
        sub.recordBillingFailure(NOW, 100, BASE, MAX); // 4h
        sub.recordBillingFailure(NOW, 100, BASE, MAX); // 8h
        sub.recordBillingFailure(NOW, 100, BASE, MAX); // 16h
        sub.recordBillingFailure(NOW, 100, BASE, MAX); // 32h -> capped to 24h

        assertThat(sub.getNextBillingAt()).isEqualTo(NOW.plus(MAX));
    }

    @Test
    void reachingMaxAttempts_autoPausesAndClearsNextBilling() {
        Subscription sub = activeSubscription();

        sub.recordBillingFailure(NOW, 3, BASE, MAX);
        sub.recordBillingFailure(NOW, 3, BASE, MAX);
        sub.recordBillingFailure(NOW, 3, BASE, MAX); // third = maxAttempts

        assertThat(sub.getFailedAttempts()).isEqualTo(3);
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.PAUSED);
        assertThat(sub.getNextBillingAt()).isNull();
    }

    @Test
    void singleAttemptBudget_pausesImmediatelyOnFirstFailure() {
        Subscription sub = activeSubscription();

        sub.recordBillingFailure(NOW, 1, BASE, MAX);

        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.PAUSED);
        assertThat(sub.getNextBillingAt()).isNull();
    }

    @Test
    void successfulAdvance_resetsFailureState() {
        Subscription sub = activeSubscription();
        sub.recordBillingFailure(NOW, 3, BASE, MAX);
        assertThat(sub.getFailedAttempts()).isEqualTo(1);

        sub.advanceCycle(NOW.plusMonths(1));

        assertThat(sub.getFailedAttempts()).isEqualTo(0);
        assertThat(sub.getLastFailureAt()).isNull();
        assertThat(sub.getCycleNo()).isEqualTo(1);
    }

    @Test
    void resumeAfterAutoPause_clearsFailureBudget() {
        Subscription sub = activeSubscription();
        sub.recordBillingFailure(NOW, 1, BASE, MAX); // auto-paused
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.PAUSED);

        sub.resume(NOW.plusMonths(1));

        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(sub.getFailedAttempts()).isEqualTo(0);
        assertThat(sub.getLastFailureAt()).isNull();
    }
}
