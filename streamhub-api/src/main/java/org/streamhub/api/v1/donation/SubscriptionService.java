package org.streamhub.api.v1.donation;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResInfinityList;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.base.security.AdminPrincipal;
import org.streamhub.api.v1.actionlog.ActionLogPublisher;
import org.streamhub.api.v1.donation.dto.SubscriptionDetail;
import org.streamhub.api.v1.donation.dto.SubscriptionListItem;
import org.streamhub.api.v1.donation.dto.SubscriptionSearchRequest;
import org.streamhub.api.v1.donation.dto.SubscriptionStatusRequest;
import org.streamhub.api.v1.donation.entity.Subscription;
import org.streamhub.api.v1.donation.entity.SubscriptionPlan;
import org.streamhub.api.v1.donation.mapper.SubscriptionMapper;
import org.streamhub.api.v1.donation.repository.SubscriptionPlanRepository;
import org.streamhub.api.v1.donation.repository.SubscriptionRepository;
import org.streamhub.api.v1.member.entity.Member;
import org.streamhub.api.v1.member.repository.MemberRepository;

/**
 * Subscription listing/detail (MyBatis joins) and lifecycle transitions. The state machine itself
 * lives on the {@link Subscription} entity; this service translates illegal transitions into
 * {@link ApiException}.
 */
@Service
public class SubscriptionService {

    private final SubscriptionMapper subscriptionMapper;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final MemberRepository memberRepository;
    private final ActionLogPublisher actionLogPublisher;

    public SubscriptionService(
            SubscriptionMapper subscriptionMapper,
            SubscriptionRepository subscriptionRepository,
            SubscriptionPlanRepository planRepository,
            MemberRepository memberRepository,
            ActionLogPublisher actionLogPublisher) {
        this.subscriptionMapper = subscriptionMapper;
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.memberRepository = memberRepository;
        this.actionLogPublisher = actionLogPublisher;
    }

    /**
     * Paginated subscription list. Subscriptions carry no church column, so the filter is applied
     * through the {@code MEMBER} join; CHURCH_MANAGER operators are pinned to their own church.
     *
     * @param request   search/pagination filters
     * @param principal authenticated operator providing the church scope
     * @return the filtered, paginated subscription list
     */
    @Transactional(readOnly = true)
    public ResInfinityList<SubscriptionListItem> list(SubscriptionSearchRequest request, AdminPrincipal principal) {
        String status = request.status() == null ? null : request.status().name();
        String keyword = blankToNull(request.keyword());
        Long churchId = scopedChurchId(request.churchId(), principal);
        int size = request.pageSizeOrDefault();

        List<SubscriptionListItem> contents =
                subscriptionMapper.selectList(keyword, status, request.planId(), churchId, request.offset(), size);
        long total = subscriptionMapper.countList(keyword, status, request.planId(), churchId);
        return ResInfinityList.of(contents, total, size);
    }

    /**
     * Subscription detail. Verifies the owning member is in the operator's church first so a
     * CHURCH_MANAGER cannot read another church's subscription.
     *
     * @param id        subscription id
     * @param principal authenticated operator providing the church scope
     * @return the joined subscription detail
     */
    @Transactional(readOnly = true)
    public SubscriptionDetail getDetail(Long id, AdminPrincipal principal) {
        ensureSubscriptionInScope(id, principal);
        SubscriptionDetail detail = subscriptionMapper.selectDetail(id);
        if (detail == null) {
            throw new ApiException(ResultCode.NOT_FOUND);
        }
        return detail;
    }

    /**
     * Applies a lifecycle transition (pause/resume/cancel) enforced by the entity state machine.
     * A CHURCH_MANAGER may only mutate subscriptions owned by members in their own church.
     *
     * @param id        subscription id
     * @param request   the target status
     * @param principal authenticated operator providing the church scope
     * @return the refreshed subscription detail
     */
    @Transactional
    @CacheEvict(cacheNames = {"dashboardSummary", "dashboardTimeseries"}, allEntries = true)
    public SubscriptionDetail changeStatus(Long id, SubscriptionStatusRequest request, AdminPrincipal principal) {
        Subscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        ensureMemberInScope(subscription.getMemberId(), principal);
        try {
            switch (request.status()) {
                case PAUSED -> subscription.pause();
                case CANCELED -> subscription.cancel();
                case ACTIVE -> subscription.resume(nextBillingFrom(subscription));
            }
        } catch (IllegalStateException e) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "허용되지 않는 상태 전이입니다");
        }
        subscriptionRepository.saveAndFlush(subscription);
        actionLogPublisher.publish("SUBSCRIPTION_STATUS", "SUBSCRIPTION",
                String.valueOf(id), request.status().name());
        return getDetail(id, principal);
    }

    /** Next billing date on resume: today + the plan's billing period. */
    private LocalDateTime nextBillingFrom(Subscription subscription) {
        SubscriptionPlan plan = planRepository.findById(subscription.getPlanId())
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        return LocalDateTime.now().plusMonths(plan.getPeriodMonths());
    }

    // --- helpers -----------------------------------------------------------

    /** Resolves the church filter: CHURCH_MANAGER is pinned to its own church. */
    private Long scopedChurchId(Long requestedChurchId, AdminPrincipal principal) {
        return principal.isSystem() ? requestedChurchId : principal.churchId();
    }

    /** Loads the subscription and verifies its owning member is in the operator's church. */
    private void ensureSubscriptionInScope(Long subscriptionId, AdminPrincipal principal) {
        if (principal.isSystem()) {
            return;
        }
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        ensureMemberInScope(subscription.getMemberId(), principal);
    }

    /** Verifies the member belongs to the operator's church (SYSTEM bypasses). */
    private void ensureMemberInScope(Long memberId, AdminPrincipal principal) {
        if (principal.isSystem()) {
            return;
        }
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        if (!member.getChurchId().equals(principal.churchId())) {
            throw new ApiException(ResultCode.FORBIDDEN);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
