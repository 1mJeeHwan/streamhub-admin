package org.streamhub.api.v1.donation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.streamhub.api.v1.actionlog.ActionLogPublisher;
import org.streamhub.api.v1.donation.entity.Donation;
import org.streamhub.api.v1.donation.entity.Subscription;
import org.streamhub.api.v1.donation.entity.SubscriptionPlan;
import org.streamhub.api.v1.donation.entity.SubscriptionStatus;
import org.streamhub.api.v1.donation.repository.DonationRepository;
import org.streamhub.api.v1.donation.repository.SubscriptionPlanRepository;
import org.streamhub.api.v1.donation.repository.SubscriptionRepository;
import org.streamhub.api.v1.member.entity.MemberGrade;

/**
 * Unit tests for the billing idempotency guard in {@link BillingService#chargeOneCycle}: a
 * duplicate cycle insert that trips the {@code uk_donation_cycle} unique constraint is caught and
 * skipped — no point accrual, no cycle advance — so a scheduler double-fire or post-commit retry
 * never bills the same cycle twice.
 */
@ExtendWith(MockitoExtension.class)
class BillingServiceIdempotencyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 18, 12, 0);

    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private SubscriptionPlanRepository planRepository;
    @Mock
    private DonationRepository donationRepository;
    @Mock
    private PointLedgerWriter pointLedgerWriter;
    @Mock
    private ActionLogPublisher actionLogPublisher;

    @InjectMocks
    private BillingService billingService;

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

    private SubscriptionPlan plan() {
        return SubscriptionPlan.builder()
                .name("Gold Plan")
                .grade(MemberGrade.GOLD)
                .price(10000L)
                .periodMonths(1)
                .pointRate(5)
                .active("Y")
                .build();
    }

    @Test
    void duplicateCycleInsert_isSkippedWithoutAdvancingOrAwardingPoints() {
        Subscription sub = activeSubscription();
        when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(sub));
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan()));
        when(donationRepository.saveAndFlush(any(Donation.class)))
                .thenThrow(new DataIntegrityViolationException("uk_donation_cycle"));

        billingService.chargeOneCycle(1L, NOW);

        // Idempotency: nothing downstream of the failed insert runs.
        verify(pointLedgerWriter, never()).append(anyLong(), anyLong(), anyString(), anyLong());
        verify(subscriptionRepository, never()).saveAndFlush(any(Subscription.class));
        verify(actionLogPublisher, never()).publish(anyString(), anyString(), anyString(), anyString());
        assertThat(sub.getCycleNo()).isEqualTo(0);
    }

    @Test
    void inactiveSubscription_isSkippedEntirely() {
        Subscription sub = activeSubscription();
        sub.pause(); // now PAUSED
        when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(sub));

        billingService.chargeOneCycle(1L, NOW);

        verify(donationRepository, never()).saveAndFlush(any(Donation.class));
        verify(pointLedgerWriter, never()).append(anyLong(), anyLong(), anyString(), anyLong());
    }
}
