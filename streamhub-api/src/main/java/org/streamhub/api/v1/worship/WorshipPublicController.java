package org.streamhub.api.v1.worship;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.base.response.ResultDTO;
import org.streamhub.api.base.util.ClientIpResolver;
import org.streamhub.api.v1.analytics.PublicIngestRateLimiter;
import org.streamhub.api.v1.worship.dto.ChurchOptionDto;
import org.streamhub.api.v1.worship.dto.WorshipRegisterRequest;
import org.streamhub.api.v1.worship.dto.WorshipRegisterResponse;

/**
 * Public, unauthenticated worship/new-family registration endpoints consumed by the user
 * site ({@code streamhub-user-web}). Mapped under {@code /pub/**}, which is permitAll in
 * {@link org.streamhub.api.base.security.SecurityConfig} (no SecurityConfig change needed).
 * Demo/test mode: every submission is marked {@code test_mode='Y'} and no real notification
 * is dispatched.
 *
 * <p><b>Abuse protection:</b> {@code create} is unauthenticated and, in a real deployment, fans
 * out to an SMS send — so it is rate limited per-client (IP) by the shared
 * {@link PublicIngestRateLimiter}. Because each registration is far more expensive (and more
 * abusable, via SMS cost) than an analytics event, it is charged {@value #REGISTER_TOKEN_COST}
 * tokens, giving an IP a small burst before it is throttled with {@code 429}. The controller-level
 * gate leaves {@link WorshipService} untouched.
 */
@Tag(name = "WorshipPublic", description = "예배·새가족 공개 신청 (인증 불필요, 데모/테스트 모드)")
@RestController
@RequestMapping("/pub/v1/worship")
public class WorshipPublicController {

    /**
     * Tokens charged per public registration. Higher than an analytics event (cost 1) because each
     * submission can trigger an SMS — this caps an IP to a small burst before throttling.
     */
    static final int REGISTER_TOKEN_COST = 10;

    private final WorshipService worshipService;
    private final PublicIngestRateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;

    public WorshipPublicController(WorshipService worshipService,
                                   PublicIngestRateLimiter rateLimiter,
                                   ClientIpResolver clientIpResolver) {
        this.worshipService = worshipService;
        this.rateLimiter = rateLimiter;
        this.clientIpResolver = clientIpResolver;
    }

    @Operation(summary = "공개 신청 생성",
            description = "비회원 다단계 폼 제출 → 신청 1건 + 가족 N건(최대 5) 생성. 실제 알림 미발송. 과도한 요청은 429.")
    @PostMapping
    public ResponseEntity<ResultDTO<WorshipRegisterResponse>> create(
            @Valid @RequestBody WorshipRegisterRequest request, HttpServletRequest httpRequest) {
        if (!rateLimiter.tryAcquire(clientIpResolver.resolve(httpRequest), REGISTER_TOKEN_COST)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ResultDTO.error(ResultCode.INVALID_PARAMETER, "요청이 너무 많습니다. 잠시 후 다시 시도해주세요."));
        }
        return ResponseEntity.ok(ResultDTO.ok(worshipService.create(request)));
    }

    @Operation(summary = "공개 교회 목록", description = "신청 폼의 교회 선택용 (openYn='Y'만).")
    @GetMapping("/churches")
    public ResultDTO<List<ChurchOptionDto>> churches() {
        return ResultDTO.ok(worshipService.listOpenChurches());
    }
}
