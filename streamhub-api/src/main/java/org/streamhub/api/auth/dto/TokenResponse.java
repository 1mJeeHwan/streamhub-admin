package org.streamhub.api.auth.dto;

/**
 * Token pair returned on login and refresh.
 *
 * @param accessToken          short-lived access token (Bearer)
 * @param refreshToken         long-lived refresh token
 * @param accessTokenExpiresIn access token lifetime in seconds
 */
public record TokenResponse(String accessToken, String refreshToken, long accessTokenExpiresIn) {
}
