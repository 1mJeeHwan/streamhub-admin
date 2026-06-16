package org.streamhub.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Refresh-token exchange request.
 */
public record RefreshRequest(
        @NotBlank(message = "refreshToken이 필요합니다") String refreshToken) {
}
