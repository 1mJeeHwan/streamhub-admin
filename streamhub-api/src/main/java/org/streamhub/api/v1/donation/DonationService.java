package org.streamhub.api.v1.donation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResInfinityList;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.base.security.AdminPrincipal;
import org.streamhub.api.base.util.SortResolver;
import org.streamhub.api.v1.actionlog.ActionLogPublisher;
import org.streamhub.api.v1.donation.dto.BillingCalendarItem;
import org.streamhub.api.v1.donation.dto.BillingCalendarRequest;
import org.streamhub.api.v1.donation.dto.DonationListItem;
import org.streamhub.api.v1.donation.dto.DonationSearchRequest;
import org.streamhub.api.v1.donation.dto.OnceDonationRequest;
import org.streamhub.api.v1.donation.entity.Donation;
import org.streamhub.api.v1.donation.entity.DonationStatus;
import org.streamhub.api.v1.donation.entity.DonationType;
import org.streamhub.api.v1.donation.mapper.DonationMapper;
import org.streamhub.api.v1.donation.repository.DonationRepository;
import org.streamhub.api.v1.member.entity.Member;
import org.streamhub.api.v1.member.repository.MemberRepository;
import org.streamhub.api.v1.sms.SmsService;

/**
 * Donation history listing (MyBatis joins/filters), manual one-off donation entry, and the
 * billing-schedule calendar aggregation. All donations are test mode.
 *
 * <p>A one-off donation triggers a best-effort receipt SMS via {@link SmsService} (mock — no
 * real dispatch); a notification failure never breaks the donation entry.
 */
@Slf4j
@Service
public class DonationService {

    /** One-off donations accrue 1% of the amount as grace points (policy constant). */
    private static final long ONCE_POINT_RATE_DIVISOR = 100L;

    /** Whitelisted sort keys (DonationListItem field → SQL column) for server-side list sorting. */
    private static final Map<String, String> DONATION_SORT_COLUMNS = Map.of(
            "memberName", "m.name",
            "planName", "p.name",
            "type", "d.type",
            "amount", "d.amount",
            "cycleNo", "d.cycle_no",
            "status", "d.status",
            "pointAwarded", "d.point_awarded",
            "paidAt", "d.paid_at");

    private final DonationMapper donationMapper;
    private final DonationRepository donationRepository;
    private final MemberRepository memberRepository;
    private final PointLedgerWriter pointLedgerWriter;
    private final ActionLogPublisher actionLogPublisher;
    private final SmsService smsService;

    public DonationService(
            DonationMapper donationMapper,
            DonationRepository donationRepository,
            MemberRepository memberRepository,
            PointLedgerWriter pointLedgerWriter,
            ActionLogPublisher actionLogPublisher,
            SmsService smsService) {
        this.donationMapper = donationMapper;
        this.donationRepository = donationRepository;
        this.memberRepository = memberRepository;
        this.pointLedgerWriter = pointLedgerWriter;
        this.actionLogPublisher = actionLogPublisher;
        this.smsService = smsService;
    }

    /**
     * Paginated donation history. Donations carry no church column, so the church filter is
     * applied through the {@code MEMBER} join; CHURCH_MANAGER operators are pinned to their own
     * church (mirrors {@code PointService}).
     *
     * @param request   search/pagination filters
     * @param principal authenticated operator providing the church scope
     * @return the filtered, paginated donation list
     */
    @Transactional(readOnly = true)
    public ResInfinityList<DonationListItem> list(DonationSearchRequest request, AdminPrincipal principal) {
        String type = request.type() == null ? null : request.type().name();
        String status = request.status() == null ? null : request.status().name();
        String keyword = blankToNull(request.keyword());
        Long churchId = scopedChurchId(request.churchId(), principal);
        int size = request.pageSizeOrDefault();
        String orderBy = SortResolver.resolve(request.sortBy(), request.sortDir(),
                DONATION_SORT_COLUMNS, "d.id", "d.paid_at DESC, d.id DESC");

        List<DonationListItem> contents = donationMapper.selectList(
                keyword, type, status, request.from(), request.to(), churchId, orderBy, request.offset(), size);
        long total = donationMapper.countList(keyword, type, status, request.from(), request.to(), churchId);
        return ResInfinityList.of(contents, total, size);
    }

    /**
     * Records a manual one-off donation, accrues points, and returns the joined list row.
     * A CHURCH_MANAGER may only create donations for members in their own church.
     *
     * @param request   donation details (target member + amount)
     * @param principal authenticated operator providing the church scope
     * @return the joined donation detail row
     */
    @Transactional
    @CacheEvict(cacheNames = {"dashboardSummary", "dashboardTimeseries"}, allEntries = true)
    public DonationListItem createOnce(OnceDonationRequest request, AdminPrincipal principal) {
        Member member = memberRepository.findById(request.memberId())
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        ensureInScope(member.getChurchId(), principal);
        long point = request.amount() / ONCE_POINT_RATE_DIVISOR;
        Donation saved = donationRepository.save(Donation.builder()
                .memberId(request.memberId())
                .subscriptionId(null)
                .type(DonationType.ONCE)
                .amount(request.amount())
                .cycleNo(null)
                .status(DonationStatus.PAID)
                .pointAwarded(point)
                .testMode("Y")
                .paidAt(LocalDateTime.now())
                .build());

        pointLedgerWriter.append(request.memberId(), point, "단건 후원 적립", saved.getId());

        actionLogPublisher.publish("DONATION_ONCE", "DONATION",
                String.valueOf(saved.getId()), "₩" + request.amount());
        try {
            smsService.sendForDonation(request.memberId(), saved.getId(), request.amount());
        } catch (RuntimeException e) {
            log.warn("Failed to send donation SMS for {}: {}", saved.getId(), e.getMessage());
        }
        return donationMapper.selectDetail(saved.getId());
    }

    /**
     * Per-day billing-schedule forecast for active subscriptions. Subscriptions carry no church
     * column, so the church filter joins through {@code MEMBER}; CHURCH_MANAGER operators see only
     * their own church's upcoming charges.
     *
     * @param request   the month range
     * @param principal authenticated operator providing the church scope
     * @return per-day count/amount of upcoming charges
     */
    @Transactional(readOnly = true)
    public List<BillingCalendarItem> billingCalendar(BillingCalendarRequest request, AdminPrincipal principal) {
        Long churchId = scopedChurchId(null, principal);
        return donationMapper.billingCalendar(request.from(), request.to(), churchId);
    }

    // --- helpers -----------------------------------------------------------

    /** Resolves the church filter: CHURCH_MANAGER is pinned to its own church. */
    private Long scopedChurchId(Long requestedChurchId, AdminPrincipal principal) {
        return principal.isSystem() ? requestedChurchId : principal.churchId();
    }

    private void ensureInScope(Long churchId, AdminPrincipal principal) {
        if (!principal.isSystem() && !churchId.equals(principal.churchId())) {
            throw new ApiException(ResultCode.FORBIDDEN);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
