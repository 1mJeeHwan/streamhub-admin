package org.streamhub.api.v1.dashboard;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.streamhub.api.base.response.ResultDTO;
import org.streamhub.api.base.security.AdminPrincipal;
import org.streamhub.api.v1.dashboard.dto.DashboardSummaryResponse;
import org.streamhub.api.v1.dashboard.dto.FeedItem;
import org.streamhub.api.v1.dashboard.dto.TimeseriesResponse;

/**
 * Unified operations dashboard endpoints (SYSTEM or CHURCH_MANAGER). Read-only,
 * aggregation-only: a KPI strip, a stacked timeseries, and a real-time activity feed.
 */
@Tag(name = "Dashboard", description = "통합 운영 대시보드")
@RestController
@RequestMapping("/v1/dashboard")
@PreAuthorize("hasAnyAuthority(T(org.streamhub.api.base.security.AuthoritiesConstants).SYSTEM, "
        + "T(org.streamhub.api.base.security.AuthoritiesConstants).CHURCH_MANAGER)")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @Operation(summary = "KPI 요약",
            description = "오늘 후원·매출/신규구독/진행중주문/미답변문의/재고경고/활성구독자. Redis 캐싱(60s).")
    @GetMapping("/summary")
    public ResultDTO<DashboardSummaryResponse> summary(@AuthenticationPrincipal AdminPrincipal principal) {
        return ResultDTO.ok(dashboardService.getSummary(principal));
    }

    @Operation(summary = "후원·매출 추이",
            description = "최근 N일 굿즈매출/정기후원/단건후원 스택 시계열(빈 날짜 0 채움). Redis 캐싱(60s).")
    @GetMapping("/timeseries")
    public ResultDTO<TimeseriesResponse> timeseries(
            @RequestParam(defaultValue = "90") int days,
            @AuthenticationPrincipal AdminPrincipal principal) {
        return ResultDTO.ok(dashboardService.getTimeseries(days, principal));
    }

    @Operation(summary = "실시간 활동 피드",
            description = "최근 활동 N건(주문·구독·후원 union, 최신순). 캐시 없음.")
    @GetMapping("/feed")
    public ResultDTO<List<FeedItem>> feed(
            @RequestParam(defaultValue = "20") int limit,
            @AuthenticationPrincipal AdminPrincipal principal) {
        return ResultDTO.ok(dashboardService.getFeed(limit, principal));
    }
}
