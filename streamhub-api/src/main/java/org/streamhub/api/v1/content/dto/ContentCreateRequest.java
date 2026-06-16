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
        List<String> hashtags) {
}
