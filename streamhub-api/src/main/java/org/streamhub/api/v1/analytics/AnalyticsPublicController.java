package org.streamhub.api.v1.analytics;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.streamhub.api.base.response.ResultDTO;
import org.streamhub.api.base.util.ClientIpResolver;
import org.streamhub.api.v1.analytics.dto.EventIngestBatchRequest;
import org.streamhub.api.v1.analytics.dto.EventIngestRequest;
import org.streamhub.api.v1.visit.VisitService;

/**
 * Public, unauthenticated analytics ingest consumed by the user site ({@code streamhub-user-web}).
 * Mapped under {@code /pub/**}, which is permitAll in
 * {@link org.streamhub.api.base.security.SecurityConfig}. This is hit directly by the browser, so
 * the service parses every field defensively and never throws on malformed input — each event is a
 * single cheap insert.
 *
 * <p>Because the endpoint is unauthenticated and one call is one DB insert, it is rate limited
 * per-client (IP) by {@link PublicIngestRateLimiter}. Over-limit requests are silently dropped and
 * still answered with {@code 200 OK} so a throttled browser never sees an error. The batch endpoint
 * caps the list size ({@link EventIngestBatchRequest}) and charges the limiter one token per event
 * so a single large batch cannot amplify past the per-IP budget.
 */
@Tag(name = "AnalyticsPublic", description = "사용자 사이트용 분석 이벤트 수집 (인증 불필요)")
@RestController
@RequestMapping("/pub/v1/events")
public class AnalyticsPublicController {

    /** Event type (string) that counts as a real site visit for the per-IP 접속 통계. */
    private static final String PAGE_VIEW = "PAGE_VIEW";

    private final AnalyticsService analyticsService;
    private final PublicIngestRateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;
    private final VisitService visitService;

    public AnalyticsPublicController(AnalyticsService analyticsService,
                                     PublicIngestRateLimiter rateLimiter,
                                     ClientIpResolver clientIpResolver,
                                     VisitService visitService) {
        this.analyticsService = analyticsService;
        this.rateLimiter = rateLimiter;
        this.clientIpResolver = clientIpResolver;
        this.visitService = visitService;
    }

    @Operation(summary = "분석 이벤트 수집", description = "페이지뷰/콘텐츠뷰/세션시작 이벤트 1건을 적재한다. 잘못된 입력은 기본값으로 보정. 과도한 요청은 무시(200).")
    @PostMapping
    public ResultDTO<Void> ingest(@RequestBody(required = false) EventIngestRequest request,
                                  HttpServletRequest httpRequest) {
        String ip = clientIpResolver.resolve(httpRequest);
        if (rateLimiter.tryAcquire("analytics:" + ip)) {
            analyticsService.ingest(request);
            recordVisit(request, ip, httpRequest.getHeader("User-Agent"));
        }
        return ResultDTO.ok();
    }

    @Operation(summary = "분석 이벤트 일괄 수집",
            description = "여러 이벤트(최대 60건)를 한 번에 적재한다. 초과 시 400. 잘못된 입력은 기본값으로 보정. 과도한 요청은 무시(200).")
    @PostMapping("/batch")
    public ResultDTO<Void> ingestBatch(@Valid @RequestBody EventIngestBatchRequest request,
                                       HttpServletRequest httpRequest) {
        int cost = request.events().size();
        String ip = clientIpResolver.resolve(httpRequest);
        if (rateLimiter.tryAcquire("analytics:" + ip, cost)) {
            analyticsService.ingestBatch(request.events());
            String userAgent = httpRequest.getHeader("User-Agent");
            request.events().forEach(e -> recordVisit(e, ip, userAgent));
        }
        return ResultDTO.ok();
    }

    /** Records a real visit (per-IP) for a PAGE_VIEW event; ignores other event types. */
    private void recordVisit(EventIngestRequest event, String ip, String userAgent) {
        if (event != null && PAGE_VIEW.equalsIgnoreCase(event.type())) {
            visitService.record(ip, userAgent, event.path(), event.deviceType(), event.memberId());
        }
    }
}
