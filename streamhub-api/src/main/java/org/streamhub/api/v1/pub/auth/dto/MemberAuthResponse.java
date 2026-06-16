package org.streamhub.api.v1.pub.auth.dto;

/** Login result: a member access token plus the logged-in profile. */
public record MemberAuthResponse(
        String token,
        long expiresIn,
        MemberInfo member) {
}
