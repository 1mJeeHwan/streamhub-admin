package org.streamhub.api.v1.pub.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** End-user login credentials (email + password). */
public record MemberLoginRequest(
        @NotBlank(message = "이메일을 입력해 주세요") String email,
        @NotBlank(message = "비밀번호를 입력해 주세요") String password) {
}
