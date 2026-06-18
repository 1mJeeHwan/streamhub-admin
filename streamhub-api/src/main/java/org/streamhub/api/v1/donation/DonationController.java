package org.streamhub.api.v1.donation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.streamhub.api.base.response.ResInfinityList;
import org.streamhub.api.base.response.ResultDTO;
import org.streamhub.api.base.security.AdminPrincipal;
import org.streamhub.api.v1.donation.dto.BillingCalendarItem;
import org.streamhub.api.v1.donation.dto.BillingCalendarRequest;
import org.streamhub.api.v1.donation.dto.DonationListItem;
import org.streamhub.api.v1.donation.dto.DonationSearchRequest;
import org.streamhub.api.v1.donation.dto.OnceDonationRequest;

/**
 * Donation history, one-off donation entry, billing calendar, and the manual demo billing trigger
 * (SYSTEM or CHURCH_MANAGER; the manual trigger is SYSTEM-only).
 */
@Tag(name = "Donation", description = "후원 내역 / 결제일정 관리")
@RestController
@RequestMapping("/v1/donation")
@PreAuthorize("hasAnyAuthority(T(org.streamhub.api.base.security.AuthoritiesConstants).SYSTEM, "
        + "T(org.streamhub.api.base.security.AuthoritiesConstants).CHURCH_MANAGER)")
public class DonationController {

    private final DonationService donationService;
    private final BillingScheduler billingScheduler;

    public DonationController(DonationService donationService, BillingScheduler billingScheduler) {
        this.donationService = donationService;
        this.billingScheduler = billingScheduler;
    }

    @Operation(summary = "후원 내역 목록", description = "유형/상태/기간 필터 + 페이지네이션.")
    @PostMapping("/list")
    public ResultDTO<ResInfinityList<DonationListItem>> list(
            @RequestBody DonationSearchRequest request,
            @AuthenticationPrincipal AdminPrincipal principal) {
        return ResultDTO.ok(donationService.list(request, principal));
    }

    @Operation(summary = "단건 후원 등록", description = "수기 단건 후원 등록(테스트 모드).")
    @PostMapping("/once")
    public ResultDTO<DonationListItem> createOnce(
            @Valid @RequestBody OnceDonationRequest request,
            @AuthenticationPrincipal AdminPrincipal principal) {
        return ResultDTO.ok(donationService.createOnce(request, principal));
    }

    @Operation(summary = "결제일정 캘린더", description = "활성 구독의 월별 청구 예정 집계(날짜별 건수·금액).")
    @PostMapping("/calendar")
    public ResultDTO<List<BillingCalendarItem>> calendar(
            @RequestBody BillingCalendarRequest request,
            @AuthenticationPrincipal AdminPrincipal principal) {
        return ResultDTO.ok(donationService.billingCalendar(request, principal));
    }

    @Operation(summary = "정기청구 수동 실행", description = "데모용 — 도래한 구독을 즉시 청구하고 처리 건수를 반환한다(SYSTEM 전용).")
    @PostMapping("/run-billing")
    @PreAuthorize("hasAuthority(T(org.streamhub.api.base.security.AuthoritiesConstants).SYSTEM)")
    public ResultDTO<Integer> runBilling() {
        return ResultDTO.ok(billingScheduler.runDueBillingNow());
    }
}
