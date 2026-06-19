package org.streamhub.api.v1.album.hls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;

/**
 * Unit tests for {@link SsrfGuard}: loopback, link-local (incl. the IMDS address), private RFC1918,
 * and IPv6 unique-local addresses are rejected; non-http(s) schemes are rejected; public addresses
 * (and literal public IPs / SoundHelix-style https URLs) are allowed.
 */
class SsrfGuardTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1/a",          // loopback
            "http://127.5.6.7/a",          // loopback /8
            "http://[::1]/a",              // IPv6 loopback
            "http://169.254.169.254/latest/meta-data/",  // link-local IMDS
            "http://169.254.10.20/a",      // link-local /16
            "http://10.0.0.5/a",           // private 10/8
            "http://172.16.0.1/a",         // private 172.16/12
            "http://172.31.255.255/a",     // private 172.16/12 upper
            "http://192.168.1.1/a",        // private 192.168/16
            "http://0.0.0.0/a",            // any-local
            "http://[fc00::1]/a",          // IPv6 unique-local fc00::/8
            "http://[fd12:3456::1]/a"      // IPv6 unique-local fd00::/8
    })
    void validate_rejectsInternalAndPrivateAddresses(String url) {
        assertThatThrownBy(() -> SsrfGuard.validate(url))
                .isInstanceOf(ApiException.class)
                .extracting("resultCode").isEqualTo(ResultCode.INVALID_PARAMETER);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ftp://example.com/a",
            "file:///etc/passwd",
            "gopher://example.com/",
            "ws://example.com/"
    })
    void validate_rejectsNonHttpSchemes(String url) {
        assertThatThrownBy(() -> SsrfGuard.validate(url))
                .isInstanceOf(ApiException.class)
                .extracting("resultCode").isEqualTo(ResultCode.INVALID_PARAMETER);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not a url", "http://", "https://", "/relative/path"})
    void validate_rejectsBlankAndMalformed(String url) {
        assertThatThrownBy(() -> SsrfGuard.validate(url))
                .isInstanceOf(ApiException.class)
                .extracting("resultCode").isEqualTo(ResultCode.INVALID_PARAMETER);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://93.184.216.34/sample.mp3",       // public literal IPv4 (no DNS)
            "https://8.8.8.8/sample.mp3",             // public literal IPv4 (no DNS)
            "https://[2606:2800:220:1:248:1893:25c8:1946]/x"  // public literal IPv6 (no DNS)
    })
    void validate_allowsPublicHttpsLiteralAddresses(String url) {
        assertThatCode(() -> SsrfGuard.validate(url)).doesNotThrowAnyException();
    }

    @Test
    void isBlocked_classifiesAddresses() throws UnknownHostException {
        assertThat(SsrfGuard.isBlocked(InetAddress.getByName("127.0.0.1"))).isTrue();
        assertThat(SsrfGuard.isBlocked(InetAddress.getByName("169.254.169.254"))).isTrue();
        assertThat(SsrfGuard.isBlocked(InetAddress.getByName("10.1.2.3"))).isTrue();
        assertThat(SsrfGuard.isBlocked(InetAddress.getByName("172.20.0.1"))).isTrue();
        assertThat(SsrfGuard.isBlocked(InetAddress.getByName("192.168.0.1"))).isTrue();
        assertThat(SsrfGuard.isBlocked(InetAddress.getByName("fc00::1"))).isTrue();

        assertThat(SsrfGuard.isBlocked(InetAddress.getByName("93.184.216.34"))).isFalse();
        assertThat(SsrfGuard.isBlocked(InetAddress.getByName("8.8.8.8"))).isFalse();
        assertThat(SsrfGuard.isBlocked(InetAddress.getByName("172.32.0.1"))).isFalse(); // just outside /12
    }
}
