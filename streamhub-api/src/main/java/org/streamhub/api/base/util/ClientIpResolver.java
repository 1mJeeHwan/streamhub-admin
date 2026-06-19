package org.streamhub.api.base.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves the originating client IP for public, unauthenticated endpoints that gate by IP
 * (rate limiting). Used by every {@code /pub/**} write controller so the trust model is defined
 * in exactly one place.
 *
 * <p><b>Why not the leftmost {@code X-Forwarded-For} hop?</b> {@code X-Forwarded-For} is a list
 * that each proxy <i>appends</i> to as the request flows inward, so the rightmost entries are the
 * ones added by infrastructure we control and the leftmost entries are fully attacker-controlled
 * (a client can send {@code X-Forwarded-For: 1.2.3.4} and spoof any address). Trusting the
 * leftmost hop lets an abuser rotate a fake IP per request and defeat the per-IP limiter entirely.
 *
 * <p><b>Topology:</b> {@code client → CloudFront → Caddy → app}. By the time the request reaches
 * the app, the header reads {@code <real-client>, <cloudfront-edge>} (Caddy's {@code remote_addr}
 * is CloudFront, which CloudFront has already prepended the real client to). Only the single
 * rightmost entry (the CloudFront edge that Caddy appended) is added by trusted infrastructure;
 * the real client is the entry <i>{@code trustedProxyHops}</i> positions from the right (default
 * {@code 1}). Anything further left was supplied by the client and is ignored — even if an abuser
 * spoofs {@code X-Forwarded-For: evil}, CloudFront appends the real viewer after it, so the
 * 1-from-the-right entry is still the genuine client. With the header shorter than expected
 * (direct hit, misconfiguration), we fall back to {@link HttpServletRequest#getRemoteAddr()}
 * rather than trusting a client-controlled value.
 */
@Component
public class ClientIpResolver {

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    /**
     * Number of trusted reverse-proxy entries appended to {@code X-Forwarded-For} in front of the
     * app. The client IP is taken this many positions from the right. Default {@code 1} matches the
     * CloudFront→Caddy→app topology: at the app the header is {@code <real-client>, <cloudfront>},
     * so the genuine client sits exactly one hop from the right. Set
     * {@code app.security.trusted-proxy-hops=0} for a direct (no-proxy) deployment so
     * {@code getRemoteAddr()} is always used.
     */
    private final int trustedProxyHops;

    public ClientIpResolver(@Value("${app.security.trusted-proxy-hops:1}") int trustedProxyHops) {
        this.trustedProxyHops = Math.max(0, trustedProxyHops);
    }

    /**
     * Resolves the client IP to gate on, honoring only the trusted (right-hand) portion of
     * {@code X-Forwarded-For} and never the attacker-controlled leftmost hop.
     *
     * @param request the inbound request (may be {@code null} in tests)
     * @return the resolved client IP, or {@code null} when it cannot be determined
     */
    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        if (trustedProxyHops > 0) {
            String forwarded = request.getHeader(FORWARDED_FOR_HEADER);
            if (forwarded != null && !forwarded.isBlank()) {
                String trusted = trustedClientHop(forwarded);
                if (trusted != null) {
                    return trusted;
                }
            }
        }
        return request.getRemoteAddr();
    }

    /**
     * Picks the {@code trustedProxyHops}-from-the-right entry of the comma-separated forwarded
     * chain. Returns {@code null} when the chain is shorter than the trusted-hop count (so the
     * caller falls back to {@code getRemoteAddr()} instead of trusting a client-supplied hop).
     */
    private String trustedClientHop(String forwarded) {
        String[] hops = forwarded.split(",");
        int clientIndex = hops.length - 1 - trustedProxyHops;
        if (clientIndex < 0) {
            return null;
        }
        String candidate = hops[clientIndex].trim();
        return candidate.isEmpty() ? null : candidate;
    }
}
