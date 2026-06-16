package org.streamhub.api.v1.member.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Editable member profile fields.
 *
 * @param name   member name (required)
 * @param phone  contact number
 * @param liveYn "Y"/"N" live-watch permission
 */
public record MemberUpdateRequest(
        @NotBlank(message = "이름을 입력하세요") String name,
        String phone,
        @NotBlank(message = "liveYn은 필수입니다") String liveYn) {
}
