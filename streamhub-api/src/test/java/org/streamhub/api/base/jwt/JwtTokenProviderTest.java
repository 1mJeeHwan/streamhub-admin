package org.streamhub.api.base.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.base.security.AdminPrincipal;
import org.streamhub.api.base.security.AuthoritiesConstants;
import org.streamhub.api.v1.admin.entity.AdminAccount;
import org.streamhub.api.v1.admin.entity.Role;
import org.streamhub.api.v1.member.entity.Member;
import org.streamhub.api.v1.member.entity.UserStatus;

class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-key-long-enough-for-hmac512-please-0123456789";

    private JwtTokenProvider provider;
    private AdminAccount admin;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(SECRET, 3600, 28800, 28800);
        admin = AdminAccount.builder()
                .loginId("admin")
                .password("hash")
                .name("홍길동")
                .role(Role.SYSTEM)
                .churchId(null)
                .build();
        ReflectionTestUtils.setField(admin, "id", 1L);
    }

    @Test
    void accessToken_carriesIdentityClaims_andVerifies() {
        String token = provider.createAccessToken(admin);

        DecodedJWT jwt = provider.verify(token);

        assertThat(jwt.getSubject()).isEqualTo("1");
        assertThat(jwt.getClaim("role").asString()).isEqualTo("SYSTEM");
        assertThat(jwt.getClaim("name").asString()).isEqualTo("홍길동");
        assertThat(provider.isRefreshToken(jwt)).isFalse();
    }

    @Test
    void getAuthentication_buildsAdminPrincipalWithRoleAuthority() {
        DecodedJWT jwt = provider.verify(provider.createAccessToken(admin));

        Authentication auth = provider.getAuthentication(jwt);

        assertThat(auth.getPrincipal()).isInstanceOf(AdminPrincipal.class);
        AdminPrincipal principal = (AdminPrincipal) auth.getPrincipal();
        assertThat(principal.id()).isEqualTo(1L);
        assertThat(principal.isSystem()).isTrue();
        assertThat(auth.getAuthorities())
                .extracting(Object::toString)
                .containsExactly(AuthoritiesConstants.SYSTEM);
    }

    @Test
    void memberToken_isIsolatedFromAdminAuthority() {
        Member member = Member.builder()
                .churchId(1L)
                .email("member01@streamhub.test")
                .password("hash")
                .name("김민준")
                .userStatus(UserStatus.CONFIRMED)
                .liveYn("Y")
                .build();
        ReflectionTestUtils.setField(member, "id", 9L);

        DecodedJWT jwt = provider.verify(provider.createMemberAccessToken(member));

        assertThat(jwt.getSubject()).isEqualTo("9");
        assertThat(provider.isMemberToken(jwt)).isTrue();
        assertThat(provider.isRefreshToken(jwt)).isFalse();
        // A member token must never carry a role — that is what keeps it out of admin endpoints.
        assertThat(jwt.getClaim("role").asString()).isNull();
    }

    @Test
    void refreshToken_isDetectedAsRefresh() {
        DecodedJWT jwt = provider.verify(provider.createRefreshToken(admin));

        assertThat(provider.isRefreshToken(jwt)).isTrue();
    }

    @Test
    void verify_invalidToken_throwsInvalidToken() {
        assertThatThrownBy(() -> provider.verify("not-a-real-jwt"))
                .isInstanceOf(ApiException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.INVALID_TOKEN);
    }

    @Test
    void verify_expiredToken_throwsTokenExpired() {
        JwtTokenProvider expiredProvider = new JwtTokenProvider(SECRET, -10, -10, -10);
        String expired = expiredProvider.createAccessToken(admin);

        assertThatThrownBy(() -> expiredProvider.verify(expired))
                .isInstanceOf(ApiException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.TOKEN_EXPIRED);
    }
}
