package org.streamhub.api.base.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.base.security.AdminPrincipal;
import org.streamhub.api.v1.admin.entity.AdminAccount;
import org.streamhub.api.v1.member.entity.Member;

/**
 * Creates and verifies HMAC512-signed JWTs.
 *
 * <p>Access tokens carry identity + role claims; refresh tokens carry only the subject.
 * Mirrors the real platform's stateless token scheme.
 */
@org.springframework.stereotype.Component
public class JwtTokenProvider {

    private static final String CLAIM_NAME = "name";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_CHURCH_ID = "church_id";
    private static final String CLAIM_EMAIL = "email";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";
    private static final String TYPE_MEMBER = "member";
    private static final String CLAIM_TYPE = "type";

    private final Algorithm algorithm;
    private final JWTVerifier verifier;
    private final long accessExpSeconds;
    private final long refreshExpSeconds;
    private final long memberExpSeconds;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-exp-seconds:3600}") long accessExpSeconds,
            @Value("${jwt.refresh-exp-seconds:28800}") long refreshExpSeconds,
            @Value("${jwt.member-exp-seconds:28800}") long memberExpSeconds) {
        this.algorithm = Algorithm.HMAC512(secret);
        this.verifier = JWT.require(algorithm).build();
        this.accessExpSeconds = accessExpSeconds;
        this.refreshExpSeconds = refreshExpSeconds;
        this.memberExpSeconds = memberExpSeconds;
    }

    /** Issues an access token with identity and role claims. */
    public String createAccessToken(AdminAccount admin) {
        Instant now = Instant.now();
        return JWT.create()
                .withSubject(String.valueOf(admin.getId()))
                .withClaim(CLAIM_NAME, admin.getName())
                .withClaim(CLAIM_ROLE, admin.getRole().name())
                .withClaim(CLAIM_CHURCH_ID, admin.getChurchId())
                .withClaim(CLAIM_TYPE, TYPE_ACCESS)
                .withIssuedAt(now)
                .withExpiresAt(now.plus(accessExpSeconds, ChronoUnit.SECONDS))
                .sign(algorithm);
    }

    /** Issues a refresh token (subject only). */
    public String createRefreshToken(AdminAccount admin) {
        Instant now = Instant.now();
        return JWT.create()
                .withSubject(String.valueOf(admin.getId()))
                .withClaim(CLAIM_TYPE, TYPE_REFRESH)
                .withIssuedAt(now)
                .withExpiresAt(now.plus(refreshExpSeconds, ChronoUnit.SECONDS))
                .sign(algorithm);
    }

    /**
     * Verifies a token, mapping failures to {@link ApiException}.
     *
     * @throws ApiException with {@link ResultCode#TOKEN_EXPIRED} or {@link ResultCode#INVALID_TOKEN}
     */
    public DecodedJWT verify(String token) {
        try {
            return verifier.verify(token);
        } catch (TokenExpiredException e) {
            throw new ApiException(ResultCode.TOKEN_EXPIRED);
        } catch (RuntimeException e) {
            throw new ApiException(ResultCode.INVALID_TOKEN);
        }
    }

    /** Builds a Spring Security authentication from a verified access token. */
    public Authentication getAuthentication(DecodedJWT jwt) {
        String role = jwt.getClaim(CLAIM_ROLE).asString();
        Long adminId = Long.valueOf(jwt.getSubject());
        Long churchId = jwt.getClaim(CLAIM_CHURCH_ID).isNull()
                ? null
                : jwt.getClaim(CLAIM_CHURCH_ID).asLong();
        AdminPrincipal principal = new AdminPrincipal(adminId, role, churchId);
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(role));
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    /** Issues a member access token (end-user site login). Carries id/name/email, never a role. */
    public String createMemberAccessToken(Member member) {
        Instant now = Instant.now();
        return JWT.create()
                .withSubject(String.valueOf(member.getId()))
                .withClaim(CLAIM_NAME, member.getName())
                .withClaim(CLAIM_EMAIL, member.getEmail())
                .withClaim(CLAIM_TYPE, TYPE_MEMBER)
                .withIssuedAt(now)
                .withExpiresAt(now.plus(memberExpSeconds, ChronoUnit.SECONDS))
                .sign(algorithm);
    }

    public boolean isRefreshToken(DecodedJWT jwt) {
        return TYPE_REFRESH.equals(jwt.getClaim(CLAIM_TYPE).asString());
    }

    /** A member token authenticates an end user — it must never grant admin authorities. */
    public boolean isMemberToken(DecodedJWT jwt) {
        return TYPE_MEMBER.equals(jwt.getClaim(CLAIM_TYPE).asString());
    }

    public long getMemberExpSeconds() {
        return memberExpSeconds;
    }

    public long getAccessExpSeconds() {
        return accessExpSeconds;
    }

    public long getRefreshExpSeconds() {
        return refreshExpSeconds;
    }
}
