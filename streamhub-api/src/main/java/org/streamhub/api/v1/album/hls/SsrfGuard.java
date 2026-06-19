package org.streamhub.api.v1.album.hls;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;

/**
 * Validates outbound URLs before the server fetches them, to prevent SSRF.
 *
 * <p>An admin/CHURCH_MANAGER can set a track's {@code previewUrl} to free text; that URL is later
 * fetched server-side to package its audio. Without validation the URL could point at internal
 * services or cloud metadata (e.g. {@code 169.254.169.254}). This guard requires an
 * {@code http}/{@code https} scheme and rejects any URL whose host resolves to a loopback,
 * link-local, private (RFC1918), or IPv6 unique-local address.
 *
 * <p>Redirect following must also be disabled on the {@link java.net.http.HttpClient} that performs
 * the fetch ({@code .followRedirects(HttpClient.Redirect.NEVER)}); otherwise an allowed public host
 * could 302 to a blocked internal address after this check has passed.
 */
public final class SsrfGuard {

    private SsrfGuard() {
    }

    /**
     * Validates that {@code url} is safe to fetch server-side.
     *
     * <p>Resolves the host (every A/AAAA record) and rejects the request if the scheme is not
     * {@code http}/{@code https} or if any resolved address is loopback, link-local, private
     * RFC1918, or IPv6 unique-local.
     *
     * @param url the URL the caller intends to fetch
     * @return the parsed {@link URI} (already validated) for convenience
     * @throws ApiException {@code INVALID_PARAMETER} if the URL is malformed or not allowed
     */
    public static URI validate(String url) {
        if (url == null || url.isBlank()) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "허용되지 않은 URL입니다");
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "허용되지 않은 URL입니다");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "허용되지 않은 URL입니다");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "허용되지 않은 URL입니다");
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "허용되지 않은 URL입니다");
        }
        for (InetAddress address : addresses) {
            if (isBlocked(address)) {
                throw new ApiException(ResultCode.INVALID_PARAMETER, "허용되지 않은 URL입니다");
            }
        }
        return uri;
    }

    /**
     * Returns {@code true} if {@code address} is in a blocked range: loopback (127.0.0.0/8, ::1),
     * link-local (169.254.0.0/16 incl. the {@code 169.254.169.254} IMDS address, fe80::/10),
     * private RFC1918 (10/8, 172.16/12, 192.168/16), or IPv6 unique-local (fc00::/7).
     */
    static boolean isBlocked(InetAddress address) {
        if (address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isAnyLocalAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            return isBlockedIpv4(bytes);
        }
        if (bytes.length == 16) {
            // fc00::/7 unique-local (covers fc00::/8 and fd00::/8); not flagged by isSiteLocalAddress.
            return (bytes[0] & 0xFE) == 0xFC;
        }
        return true;
    }

    private static boolean isBlockedIpv4(byte[] bytes) {
        int first = bytes[0] & 0xFF;
        int second = bytes[1] & 0xFF;
        // 10.0.0.0/8
        if (first == 10) {
            return true;
        }
        // 172.16.0.0/12
        if (first == 172 && second >= 16 && second <= 31) {
            return true;
        }
        // 192.168.0.0/16
        if (first == 192 && second == 168) {
            return true;
        }
        // 169.254.0.0/16 (link-local incl. 169.254.169.254 IMDS) — also caught by isLinkLocalAddress.
        if (first == 169 && second == 254) {
            return true;
        }
        // 127.0.0.0/8 loopback — also caught by isLoopbackAddress.
        return first == 127;
    }
}
