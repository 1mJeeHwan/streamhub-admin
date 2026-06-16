package org.streamhub.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Login credentials.
 */
public record LoginRequest(
        @NotBlank(message = "아이디를 입력하세요") String loginId,
        @NotBlank(message = "비밀번호를 입력하세요") String password) {
}
