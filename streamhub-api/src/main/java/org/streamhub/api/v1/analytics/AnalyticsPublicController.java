package org.streamhub.api.v1.analytics;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.streamhub.api.base.response.ResultDTO;
import org.streamhub.api.v1.analytics.dto.EventIngestRequest;

/**
 * Public, unauthenticated analytics ingest consumed by the user site ({@code streamhub-user-web}).
 * Mapped under {@code /pub/**}, which is permitAll in
 * {@link org.streamhub.api.base.security.SecurityConfig}. This is hit directly by the browser, so
 * the service parses every field defensively and never throws on malformed input — each event is a
 * single cheap insert.
 *
 * <p>Because the endpoint is unauthenticated and one call is one DB insert, it is rate limited
 * per-client (IP) by {@link PublicIngestRateLimiter}. Over-limit requests are silently dropped and
 * still answered with {@code 200 OK} so a throttled browser never sees an error.
 */
@Tag(name = "AnalyticsPublic", description = "사용자 사이트용 분석 이벤트 수집 (인증 불필요)")
@RestController
@RequestMapping("/pub/v1/events")
public class AnalyticsPublicController {

    private final AnalyticsService analyticsService;
    private final PublicIngestRateLimiter rateLimiter;

    public AnalyticsPublicController(AnalyticsService analyticsService,
                                     PublicIngestRateLimiter rateLimiter) {
        this.analyticsService = analyticsService;
        this.rateLimiter = rateLimiter;
    }

    @Operation(summary = "분석 이벤트 수집", description = "페이지뷰/콘텐츠뷰/세션시작 이벤트 1건을 적재한다. 잘못된 입력은 기본값으로 보정. 과도한 요청은 무시(200).")
    @PostMapping
    public ResultDTO<Void> ingest(@RequestBody(required = false) EventIngestRequest request,
                                  HttpServletRequest httpRequest) {
        if (rateLimiter.tryAcquire(clientIp(httpRequest))) {
            analyticsService.ingest(request);
        }
        return ResultDTO.ok();
    }

    @Operation(summary = "분석 이벤트 일괄 수집", description = "여러 이벤트를 한 번에 적재한다. 잘못된 입력은 기본값으로 보정. 과도한 요청은 무시(200).")
    @PostMapping("/batch")
    public ResultDTO<Void> ingestBatch(@RequestBody(required = false) List<EventIngestRequest> requests,
                                       HttpServletRequest httpRequest) {
        if (rateLimiter.tryAcquire(clientIp(httpRequest))) {
            analyticsService.ingestBatch(requests);
        }
        return ResultDTO.ok();
    }

    /**
     * Resolves the originating client IP, honoring the first hop of {@code X-Forwarded-For} when
     * present (the app sits behind a proxy/LB) and falling back to the socket address.
     */
    private String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
