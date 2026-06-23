package org.streamhub.api.v1.pub.auth;

import com.auth0.jwt.interfaces.DecodedJWT;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.jwt.JwtTokenProvider;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.base.response.ResultDTO;
import org.streamhub.api.base.util.ClientIpResolver;
import org.streamhub.api.v1.analytics.PublicIngestRateLimiter;
import org.streamhub.api.v1.pub.auth.dto.MemberAuthResponse;
import org.streamhub.api.v1.pub.auth.dto.MemberInfo;
import org.streamhub.api.v1.pub.auth.dto.MemberLoginRequest;
import org.streamhub.api.v1.pub.auth.dto.MemberSignupRequest;

/**
 * End-user authentication endpoints under the public ({@code /pub/**}, permitAll) namespace.
 * {@code /me} authenticates by parsing the member token directly — it never relies on the
 * admin SecurityContext, which deliberately ignores member tokens.
 */
@Tag(name = "Member Auth", description = "사용자 사이트 로그인 (회원)")
@RestController
@RequestMapping("/pub/v1/auth")
public class MemberAuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * Token cost per signup/login attempt charged to the shared {@link PublicIngestRateLimiter}
     * (capacity 60, refill 5/s). At cost 12 an IP gets a burst of ~5 attempts and a steady ~25/min
     * before throttling — comfortable for humans, hostile to credential-stuffing/signup floods.
     */
    private static final int AUTH_RATE_COST = 12;

    private final MemberAuthService memberAuthService;
    private final JwtTokenProvider tokenProvider;
    private final PublicIngestRateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;

    public MemberAuthController(MemberAuthService memberAuthService,
                               JwtTokenProvider tokenProvider,
                               PublicIngestRateLimiter rateLimiter,
                               ClientIpResolver clientIpResolver) {
        this.memberAuthService = memberAuthService;
        this.tokenProvider = tokenProvider;
        this.rateLimiter = rateLimiter;
        this.clientIpResolver = clientIpResolver;
    }

    @Operation(summary = "회원 로그인", description = "이메일/비밀번호로 로그인하고 회원 토큰을 발급한다. 과도한 요청은 차단.")
    @PostMapping("/login")
    public ResultDTO<MemberAuthResponse> login(@Valid @RequestBody MemberLoginRequest request,
                                               HttpServletRequest httpRequest) {
        enforceRateLimit("memberLogin", httpRequest);
        return ResultDTO.ok(memberAuthService.login(request));
    }

    @Operation(summary = "회원가입",
            description = "약관 동의 후 회원을 생성하고 회원 토큰을 발급한다(가입 즉시 로그인). 과도한 요청은 차단.")
    @PostMapping("/signup")
    public ResultDTO<MemberAuthResponse> signup(@Valid @RequestBody MemberSignupRequest request,
                                                HttpServletRequest httpRequest) {
        enforceRateLimit("memberSignup", httpRequest);
        return ResultDTO.ok(memberAuthService.signup(request));
    }

    /**
     * Per-client-IP throttle for the unauthenticated auth endpoints. On exceed, rejects with
     * {@link ResultCode#INVALID_PARAMETER} (no dedicated 429 code exists) and a "too many requests"
     * message — mirroring {@code ChatController}.
     */
    private void enforceRateLimit(String bucket, HttpServletRequest httpRequest) {
        if (!rateLimiter.tryAcquire(bucket + ":" + clientIpResolver.resolve(httpRequest), AUTH_RATE_COST)) {
            throw new ApiException(ResultCode.INVALID_PARAMETER,
                    "요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    @Operation(summary = "내 정보", description = "회원 토큰으로 로그인한 회원의 프로필을 반환한다.")
    @GetMapping("/me")
    public ResultDTO<MemberInfo> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return ResultDTO.ok(memberAuthService.me(resolveMemberId(authorization)));
    }

    private Long resolveMemberId(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            throw new ApiException(ResultCode.UNAUTHORIZED);
        }
        DecodedJWT jwt = tokenProvider.verify(authorization.substring(BEARER_PREFIX.length()));
        if (!tokenProvider.isMemberToken(jwt)) {
            throw new ApiException(ResultCode.INVALID_TOKEN);
        }
        return Long.valueOf(jwt.getSubject());
    }
}
