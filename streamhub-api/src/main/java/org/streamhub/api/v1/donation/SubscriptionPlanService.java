package org.streamhub.api.v1.donation;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.v1.actionlog.ActionLogPublisher;
import org.streamhub.api.v1.donation.dto.PlanCreateRequest;
import org.streamhub.api.v1.donation.dto.PlanResponse;
import org.streamhub.api.v1.donation.entity.SubscriptionPlan;
import org.streamhub.api.v1.donation.entity.SubscriptionStatus;
import org.streamhub.api.v1.donation.repository.SubscriptionPlanRepository;
import org.streamhub.api.v1.donation.repository.SubscriptionRepository;

/**
 * Membership-plan CRUD. Each response carries the live count of active subscriptions on the plan.
 *
 * <p><b>Intentionally NOT church-scoped.</b> {@code SUBSCRIPTION_PLAN} is a global catalog keyed by
 * {@link org.streamhub.api.v1.member.entity.MemberGrade} — a plan has no owning church column and is
 * shared across every church (subscriptions reference plans by {@code plan_id}). There is therefore
 * nothing to scope on here: listing and detail are global by design, and the active-subscription
 * count in {@link #toResponse} is a deliberate cross-church total. Per-church isolation is enforced
 * on the things that DO carry a member/church linkage — subscriptions, donations and the dashboard.
 * The destructive delete is already restricted to SYSTEM at the controller layer.
 */
@Service
public class SubscriptionPlanService {

    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ActionLogPublisher actionLogPublisher;

    public SubscriptionPlanService(
            SubscriptionPlanRepository planRepository,
            SubscriptionRepository subscriptionRepository,
            ActionLogPublisher actionLogPublisher) {
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.actionLogPublisher = actionLogPublisher;
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> list() {
        return planRepository.findAllByOrderByPriceAscIdAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PlanResponse getDetail(Long id) {
        return toResponse(findPlan(id));
    }

    @Transactional
    public PlanResponse create(PlanCreateRequest request) {
        SubscriptionPlan plan = SubscriptionPlan.builder()
                .name(request.name())
                .grade(request.grade())
                .price(request.price())
                .periodMonths(request.periodMonths())
                .pointRate(request.pointRate())
                .benefit(request.benefit())
                .active(request.active())
                .build();
        SubscriptionPlan saved = planRepository.save(plan);
        actionLogPublisher.publish("PLAN_CREATE", "SUBSCRIPTION_PLAN",
                String.valueOf(saved.getId()), request.name());
        return toResponse(saved);
    }

    @Transactional
    public PlanResponse update(Long id, PlanCreateRequest request) {
        SubscriptionPlan plan = findPlan(id);
        plan.update(request.name(), request.grade(), request.price(), request.periodMonths(),
                request.pointRate(), request.benefit(), request.active());
        planRepository.saveAndFlush(plan);
        actionLogPublisher.publish("PLAN_UPDATE", "SUBSCRIPTION_PLAN",
                String.valueOf(id), request.name());
        return toResponse(plan);
    }

    /**
     * Deletes a plan, but refuses while any ACTIVE subscription still references it. Hard-deleting a
     * referenced plan would make every subscriber's next {@link BillingService#chargeOneCycle}
     * throw {@code NOT_FOUND} on the missing plan and silently drop out of billing, so the delete
     * is blocked with {@link ResultCode#INVALID_PARAMETER}. Operators should pause/cancel or migrate
     * the active subscribers (or simply deactivate the plan) before removing it.
     *
     * @param id the plan id to delete
     * @throws ApiException {@code INVALID_PARAMETER} when active subscriptions still reference the plan
     */
    @Transactional
    public void delete(Long id) {
        SubscriptionPlan plan = findPlan(id);
        long activeCount =
                subscriptionRepository.countByPlanIdAndStatus(id, SubscriptionStatus.ACTIVE);
        if (activeCount > 0) {
            throw new ApiException(ResultCode.INVALID_PARAMETER,
                    "활성 구독자가 " + activeCount + "명 있어 플랜을 삭제할 수 없습니다. 구독 해지 또는 플랜 비활성화 후 다시 시도하세요.");
        }
        planRepository.delete(plan);
        actionLogPublisher.publish("PLAN_DELETE", "SUBSCRIPTION_PLAN",
                String.valueOf(id), plan.getName());
    }

    private SubscriptionPlan findPlan(Long id) {
        return planRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
    }

    private PlanResponse toResponse(SubscriptionPlan plan) {
        long activeCount =
                subscriptionRepository.countByPlanIdAndStatus(plan.getId(), SubscriptionStatus.ACTIVE);
        return PlanResponse.of(plan, activeCount);
    }
}
