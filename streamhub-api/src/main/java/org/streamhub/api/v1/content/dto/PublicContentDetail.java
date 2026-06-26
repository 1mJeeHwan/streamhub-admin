package org.streamhub.api.v1.content.dto;

import java.time.LocalDateTime;
import java.util.List;
import org.streamhub.api.v1.content.entity.ContentType;

/**
 * Content detail exposed to the <b>public</b> user site. A curated subset of {@link ContentDetail} —
 * it omits fields anonymous callers must not see: the internal {@code status}, the raw storage key
 * ({@code thumbnailKey}, which would aid bucket-path enumeration), and the {@code updatedAt} audit
 * timestamp. Playback fields ({@code mediaUrl}/{@code hlsPrefix}) and the resolved
 * {@code thumbnailUrl} are kept.
 */
public record PublicContentDetail(
        Long id,
        String title,
        String description,
        ContentType type,
        Long channelId,
        String channelName,
        String churchName,
        String mediaUrl,
        String hlsPrefix,
        String thumbnailUrl,
        Integer durationSec,
        Long viewCount,
        List<String> hashtags,
        List<ContentFileDto> files,
        LocalDateTime createdAt) {

    /** Projects the full (admin) detail down to the public-safe fields. */
    public static PublicContentDetail from(ContentDetail d) {
        return new PublicContentDetail(
                d.getId(), d.getTitle(), d.getDescription(), d.getType(),
                d.getChannelId(), d.getChannelName(), d.getChurchName(),
                d.getMediaUrl(), d.getHlsPrefix(), d.getThumbnailUrl(),
                d.getDurationSec(), d.getViewCount(), d.getHashtags(),
                d.getFiles(), d.getCreatedAt());
    }
}
