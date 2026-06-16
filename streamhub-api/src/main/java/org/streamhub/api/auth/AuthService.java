package org.streamhub.api.auth;

import com.auth0.jwt.interfaces.DecodedJWT;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.streamhub.api.auth.dto.LoginRequest;
import org.streamhub.api.auth.dto.TokenResponse;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.jwt.JwtTokenProvider;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.v1.admin.entity.AdminAccount;
import org.streamhub.api.v1.admin.repository.AdminAccountRepository;

/**
 * Authentication: credential check, token issuance, refresh rotation, and logout.
 *
 * <p>Refresh tokens are whitelisted in Redis ({@code refresh:<adminId>}) so logout can
 * invalidate them and rotation can detect reuse of a stale token.
 */
@Service
public class AuthService {

    private static final String REFRESH_KEY_PREFIX = "refresh:";

    private final AdminAccountRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final StringRedisTemplate redisTemplate;
    private final org.streamhub.api.v1.actionlog.ActionLogPublisher actionLogPublisher;

    public AuthService(
            AdminAccountRepository adminRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider tokenProvider,
            StringRedisTemplate redisTemplate,
            org.streamhub.api.v1.actionlog.ActionLogPublisher actionLogPublisher) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.redisTemplate = redisTemplate;
        this.actionLogPublisher = actionLogPublisher;
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        AdminAccount admin = adminRepository.findByLoginId(request.loginId())
                .orElseThrow(() -> new ApiException(ResultCode.LOGIN_FAILED));
        if (!passwordEncoder.matches(request.password(), admin.getPassword())) {
            throw new ApiException(ResultCode.LOGIN_FAILED);
        }
        TokenResponse tokens = issueTokens(admin);
        actionLogPublisher.publishAs(admin.getId(), admin.getName(),
                "LOGIN", "ADMIN", String.valueOf(admin.getId()), "로그인");
        return tokens;
    }

    @Transactional(readOnly = true)
    public TokenResponse refresh(String refreshToken) {
        DecodedJWT jwt = tokenProvider.verify(refreshToken);
        if (!tokenProvider.isRefreshToken(jwt)) {
            throw new ApiException(ResultCode.INVALID_TOKEN);
        }
        Long adminId = Long.valueOf(jwt.getSubject());
        String stored = redisTemplate.opsForValue().get(REFRESH_KEY_PREFIX + adminId);
        if (stored == null || !stored.equals(refreshToken)) {
            throw new ApiException(ResultCode.INVALID_TOKEN);
        }
        AdminAccount admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new ApiException(ResultCode.INVALID_TOKEN));
        return issueTokens(admin);
    }

    public void logout(String refreshToken) {
        try {
            DecodedJWT jwt = tokenProvider.verify(refreshToken);
            redisTemplate.delete(REFRESH_KEY_PREFIX + jwt.getSubject());
        } catch (ApiException ignored) {
            // already invalid/expired — nothing to revoke
        }
    }

    private TokenResponse issueTokens(AdminAccount admin) {
        String accessToken = tokenProvider.createAccessToken(admin);
        String refreshToken = tokenProvider.createRefreshToken(admin);
        redisTemplate.opsForValue().set(
                REFRESH_KEY_PREFIX + admin.getId(),
                refreshToken,
                Duration.ofSeconds(tokenProvider.getRefreshExpSeconds()));
        return new TokenResponse(accessToken, refreshToken, tokenProvider.getAccessExpSeconds());
    }
}
