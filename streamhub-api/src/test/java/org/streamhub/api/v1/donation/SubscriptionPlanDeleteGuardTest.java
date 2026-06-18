package org.streamhub.api.v1.donation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.v1.actionlog.ActionLogPublisher;
import org.streamhub.api.v1.donation.entity.SubscriptionPlan;
import org.streamhub.api.v1.donation.entity.SubscriptionStatus;
import org.streamhub.api.v1.donation.repository.SubscriptionPlanRepository;
import org.streamhub.api.v1.donation.repository.SubscriptionRepository;
import org.streamhub.api.v1.member.entity.MemberGrade;

/**
 * Unit tests for the plan-delete guard ({@link SubscriptionPlanService#delete}): a plan with active
 * subscribers must not be hard-deleted (which would break those subscribers' next billing charge),
 * while an unreferenced plan deletes normally.
 */
@ExtendWith(MockitoExtension.class)
class SubscriptionPlanDeleteGuardTest {

    @Mock
    private SubscriptionPlanRepository planRepository;
    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private ActionLogPublisher actionLogPublisher;

    @InjectMocks
    private SubscriptionPlanService service;

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
    void delete_withActiveSubscribers_isBlocked() {
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan()));
        when(subscriptionRepository.countByPlanIdAndStatus(1L, SubscriptionStatus.ACTIVE))
                .thenReturn(2L);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getResultCode())
                        .isEqualTo(ResultCode.INVALID_PARAMETER));

        verify(planRepository, never()).delete(any());
        verify(actionLogPublisher, never()).publish(eq("PLAN_DELETE"), anyString(), anyString(), anyString());
    }

    @Test
    void delete_withNoActiveSubscribers_succeeds() {
        SubscriptionPlan plan = plan();
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        when(subscriptionRepository.countByPlanIdAndStatus(1L, SubscriptionStatus.ACTIVE))
                .thenReturn(0L);

        service.delete(1L);

        verify(planRepository).delete(plan);
        verify(actionLogPublisher).publish(eq("PLAN_DELETE"), eq("SUBSCRIPTION_PLAN"),
                eq("1"), eq("Gold Plan"));
    }
}
