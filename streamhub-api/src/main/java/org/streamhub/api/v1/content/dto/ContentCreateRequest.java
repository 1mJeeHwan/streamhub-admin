package org.streamhub.api.v1.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.streamhub.api.v1.content.entity.ContentStatus;
import org.streamhub.api.v1.content.entity.ContentType;

/**
 * Create-content payload. {@code thumbnailKey} comes from a prior /upload call.
 */
public record ContentCreateRequest(
        @NotBlank(message = "제목을 입력하세요") String title,
        String description,
        @NotNull(message = "유형은 필수입니다") ContentType type,
        @NotNull(message = "채널은 필수입니다") Long channelId,
        String mediaUrl,
        Integer durationSec,
        String thumbnailKey,
        ContentStatus status,
        List<String> hashtags,
        // 등록(PUBLISHED)시 해당 교회 회원에게 자동 공지. record라 프론트 생략 시 false. notification:write 필요.
        boolean notifyOnPublish) {
}
