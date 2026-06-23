package org.streamhub.api.v1.pub.auth;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.jwt.JwtTokenProvider;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.v1.member.entity.Church;
import org.streamhub.api.v1.member.entity.Member;
import org.streamhub.api.v1.member.entity.UserStatus;
import org.streamhub.api.v1.member.repository.ChurchRepository;
import org.streamhub.api.v1.member.repository.MemberRepository;
import org.streamhub.api.v1.pub.auth.dto.MemberAuthResponse;
import org.streamhub.api.v1.pub.auth.dto.MemberInfo;
import org.streamhub.api.v1.pub.auth.dto.MemberLoginRequest;
import org.streamhub.api.v1.pub.auth.dto.MemberSignupRequest;

/**
 * End-user (member) authentication for the public site. Issues member-scoped JWTs that
 * are isolated from admin tokens (see {@link JwtTokenProvider#createMemberAccessToken}).
 * Only {@link UserStatus#CONFIRMED} members may log in.
 */
@Service
public class MemberAuthService {

    /** Redis key prefix for the per-account login-failure counter. */
    private static final String LOGIN_FAIL_KEY_PREFIX = "memberLoginFail:";

    /** Consecutive failures (within the window) after which login is locked out. */
    private static final int MAX_LOGIN_FAILURES = 5;

    /** How long the failure counter (and therefore the lockout) lives. */
    private static final Duration LOGIN_FAIL_WINDOW = Duration.ofMinutes(10);

    private final MemberRepository memberRepository;
    private final ChurchRepository churchRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final org.streamhub.api.v1.security.SecurityMonitor securityMonitor;
    private final StringRedisTemplate redisTemplate;

    public MemberAuthService(
            MemberRepository memberRepository,
            ChurchRepository churchRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider tokenProvider,
            org.streamhub.api.v1.security.SecurityMonitor securityMonitor,
            StringRedisTemplate redisTemplate) {
        this.memberRepository = memberRepository;
        this.churchRepository = churchRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.securityMonitor = securityMonitor;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Registers a new member. Enforces email/phone uniqueness, stores a BCrypt password, and returns
     * an access token so the client is logged in immediately. New members are
     * {@link UserStatus#CONFIRMED} (self-service signup is auto-approved on this public site).
     */
    @Transactional
    public MemberAuthResponse signup(MemberSignupRequest request) {
        String phone = normalizePhone(request.phone());

        String email = request.email().trim().toLowerCase();
        // Single generic message for both email and phone collisions: distinct messages would
        // leak whether a given email/phone is already registered (account-enumeration oracle).
        // DB-level uniqueness is still the source of truth.
        if (memberRepository.existsByEmail(email) || memberRepository.existsByPhone(phone)) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "이미 가입된 계정 정보가 있습니다");
        }

        Church church = churchRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new ApiException(ResultCode.INTERNAL_ERROR, "가입 가능한 교회가 없습니다"));

        Member member = Member.builder()
                .churchId(church.getId())
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .name(request.name().trim())
                .phone(phone)
                .userStatus(UserStatus.CONFIRMED)
                .liveYn("N")
                .marketingAgreed(request.agreeMarketing())
                .build();
        memberRepository.save(member);

        String token = tokenProvider.createMemberAccessToken(member);
        return new MemberAuthResponse(token, tokenProvider.getMemberExpSeconds(), toInfo(member));
    }

    /** Formats a phone as 010-XXXX-XXXX (matching seeded members) from any digit/hyphen input. */
    private String normalizePhone(String raw) {
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() == 11) {
            return digits.substring(0, 3) + "-" + digits.substring(3, 7) + "-" + digits.substring(7);
        }
        if (digits.length() == 10) {
            return digits.substring(0, 3) + "-" + digits.substring(3, 6) + "-" + digits.substring(6);
        }
        return digits;
    }

    @Transactional(readOnly = true)
    public MemberAuthResponse login(MemberLoginRequest request) {
        String accountKey = request.email() == null ? "" : request.email().trim().toLowerCase();
        if (isLockedOut(accountKey)) {
            throw new ApiException(ResultCode.LOGIN_FAILED,
                    "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해주세요.");
        }
        Member member = memberRepository.findByEmail(request.email())
                .orElseThrow(() -> {
                    securityMonitor.recordAuthFailure(request.email(), "MEMBER");
                    recordLoginFailure(accountKey);
                    return new ApiException(ResultCode.LOGIN_FAILED);
                });
        if (!passwordEncoder.matches(request.password(), member.getPassword())) {
            securityMonitor.recordAuthFailure(request.email(), "MEMBER");
            recordLoginFailure(accountKey);
            throw new ApiException(ResultCode.LOGIN_FAILED);
        }
        if (member.getUserStatus() != UserStatus.CONFIRMED) {
            throw new ApiException(ResultCode.FORBIDDEN, "승인 대기 중이거나 비활성화된 계정입니다");
        }
        clearLoginFailures(accountKey);
        String token = tokenProvider.createMemberAccessToken(member);
        return new MemberAuthResponse(token, tokenProvider.getMemberExpSeconds(), toInfo(member));
    }

    /**
     * Best-effort lockout check: returns {@code true} when the per-account failure counter in Redis
     * has reached {@link #MAX_LOGIN_FAILURES} within {@link #LOGIN_FAIL_WINDOW}. Any Redis hiccup is
     * swallowed (fail-open) so an infra outage never blocks legitimate logins.
     */
    private boolean isLockedOut(String accountKey) {
        try {
            String count = redisTemplate.opsForValue().get(LOGIN_FAIL_KEY_PREFIX + accountKey);
            return count != null && Integer.parseInt(count) >= MAX_LOGIN_FAILURES;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /** Increments the per-account failure counter, (re)setting the sliding TTL. Best-effort. */
    private void recordLoginFailure(String accountKey) {
        try {
            Long count = redisTemplate.opsForValue().increment(LOGIN_FAIL_KEY_PREFIX + accountKey);
            if (count != null && count == 1L) {
                redisTemplate.expire(LOGIN_FAIL_KEY_PREFIX + accountKey, LOGIN_FAIL_WINDOW);
            }
        } catch (RuntimeException ignored) {
            // Redis unavailable — skip lockout bookkeeping rather than break login.
        }
    }

    /** Clears the failure counter after a successful login. Best-effort. */
    private void clearLoginFailures(String accountKey) {
        try {
            redisTemplate.delete(LOGIN_FAIL_KEY_PREFIX + accountKey);
        } catch (RuntimeException ignored) {
            // ignore
        }
    }

    @Transactional(readOnly = true)
    public MemberInfo me(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        return toInfo(member);
    }

    private MemberInfo toInfo(Member member) {
        String churchName = churchRepository.findById(member.getChurchId())
                .map(Church::getName)
                .orElse(null);
        return new MemberInfo(
                member.getId(), member.getName(), member.getEmail(),
                member.getPhone(), churchName, member.getCreatedAt());
    }
}
