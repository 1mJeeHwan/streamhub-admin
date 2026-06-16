package org.streamhub.api.v1.statistics;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.streamhub.api.base.response.ResultDTO;
import org.streamhub.api.v1.statistics.dto.ChannelWatchItem;
import org.streamhub.api.v1.statistics.dto.SummaryResponse;
import org.streamhub.api.v1.statistics.dto.TopContentItem;
import org.streamhub.api.v1.statistics.dto.TrendPoint;

/**
 * Dashboard statistics endpoints (SYSTEM or CHURCH_MANAGER).
 */
@Tag(name = "Statistics", description = "통계 대시보드")
@RestController
@RequestMapping("/v1/statistics")
@PreAuthorize("hasAnyAuthority(T(org.streamhub.api.base.security.AuthoritiesConstants).SYSTEM, "
        + "T(org.streamhub.api.base.security.AuthoritiesConstants).CHURCH_MANAGER)")
public class StatController {

    private final StatService statService;

    public StatController(StatService statService) {
        this.statService = statService;
    }

    @Operation(summary = "요약 카드", description = "총 회원/신규(7일)/총 조회수/총 콘텐츠. Redis 캐싱(60s).")
    @GetMapping("/summary")
    public ResultDTO<SummaryResponse> summary() {
        return ResultDTO.ok(statService.getSummary());
    }

    @Operation(summary = "일별 가입 추이")
    @GetMapping("/member-trend")
    public ResultDTO<List<TrendPoint>> memberTrend(@RequestParam(defaultValue = "30") int days) {
        return ResultDTO.ok(statService.getMemberTrend(days));
    }

    @Operation(summary = "조회수 Top N")
    @GetMapping("/top-contents")
    public ResultDTO<List<TopContentItem>> topContents(@RequestParam(defaultValue = "5") int limit) {
        return ResultDTO.ok(statService.getTopContents(limit));
    }

    @Operation(summary = "채널별 시청시간")
    @GetMapping("/watch-by-channel")
    public ResultDTO<List<ChannelWatchItem>> watchByChannel() {
        return ResultDTO.ok(statService.getWatchByChannel());
    }
}
