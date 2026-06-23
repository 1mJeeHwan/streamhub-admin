package org.streamhub.api.v1.pub.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Member sign-up. The phone must already have passed identity verification
 * (see {@code /pub/v1/auth/verify/confirm}); sign-up consumes that one-time verified flag.
 * The two mandatory consents ({@code agreeTerms}, {@code agreePrivacy}) are enforced both here
 * and on the client; {@code agreeMarketing} is optional and persisted on the member.
 */
public record MemberSignupRequest(
        @NotBlank(message = "이메일을 입력해 주세요")
        @Email(message = "올바른 이메일 형식이 아닙니다") String email,

        @NotBlank(message = "비밀번호를 입력해 주세요")
        @Size(min = 8, max = 64, message = "비밀번호는 8자 이상이어야 합니다") String password,

        @NotBlank(message = "이름을 입력해 주세요")
        @Size(max = 50, message = "이름은 50자 이하여야 합니다") String name,

        @NotBlank(message = "휴대폰 인증이 필요합니다") String phone,

        @AssertTrue(message = "이용약관에 동의해 주세요") boolean agreeTerms,
        @AssertTrue(message = "개인정보 수집·이용에 동의해 주세요") boolean agreePrivacy,
        boolean agreeMarketing) {
}
