package org.streamhub.api.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.streamhub.api.auth.dto.LoginRequest;
import org.streamhub.api.auth.dto.RefreshRequest;
import org.streamhub.api.auth.dto.TokenResponse;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.base.response.ResultDTO;
import org.streamhub.api.base.util.ClientIpResolver;
import org.streamhub.api.v1.analytics.PublicIngestRateLimiter;

/**
 * Authentication endpoints (public). Issues and rotates JWT token pairs.
 */
@Tag(name = "Auth", description = "인증 (로그인/토큰갱신/로그아웃)")
@RestController
@RequestMapping("/auth")
public class AuthController {

    /**
     * Token cost per login attempt charged to the shared {@link PublicIngestRateLimiter}
     * (capacity 60, refill 5/s). At cost 12 an IP gets a burst of ~5 attempts and a steady ~25/min
     * before throttling — comfortable for humans, hostile to admin-credential brute force.
     */
    private static final int LOGIN_RATE_COST = 12;

    private final AuthService authService;
    private final PublicIngestRateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;

    public AuthController(AuthService authService,
                          PublicIngestRateLimiter rateLimiter,
                          ClientIpResolver clientIpResolver) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
        this.clientIpResolver = clientIpResolver;
    }

    @Operation(summary = "로그인", description = "아이디/비밀번호로 access/refresh 토큰을 발급한다. 과도한 요청은 차단.")
    @PostMapping("/login")
    public ResultDTO<TokenResponse> login(@Valid @RequestBody LoginRequest request,
                                          HttpServletRequest httpRequest) {
        if (!rateLimiter.tryAcquire("adminLogin:" + clientIpResolver.resolve(httpRequest), LOGIN_RATE_COST)) {
            throw new ApiException(ResultCode.INVALID_PARAMETER,
                    "요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
        }
        return ResultDTO.ok(authService.login(request));
    }

    @Operation(summary = "토큰 갱신", description = "refresh 토큰으로 새 토큰 쌍을 발급한다.")
    @PostMapping("/refresh")
    public ResultDTO<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResultDTO.ok(authService.refresh(request.refreshToken()));
    }

    @Operation(summary = "로그아웃", description = "refresh 토큰을 무효화한다.")
    @PostMapping("/logout")
    public ResultDTO<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResultDTO.ok();
    }
}
