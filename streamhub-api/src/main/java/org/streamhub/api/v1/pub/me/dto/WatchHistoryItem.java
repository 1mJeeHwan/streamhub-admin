package org.streamhub.api.v1.pub.me.dto;

import java.time.LocalDateTime;
import org.streamhub.api.v1.content.entity.ContentType;

/**
 * One row of the member's watch history ("내 시청기록"), content metadata joined in.
 *
 * @param contentId    the watched content
 * @param title        content title at read time
 * @param type         VIDEO or SOUND
 * @param thumbnailUrl public thumbnail URL ({@code null} if the content has no thumbnail)
 * @param watchedAt    when it was watched
 * @param watchSeconds seconds watched
 */
public record WatchHistoryItem(
        Long contentId,
        String title,
        ContentType type,
        String thumbnailUrl,
        LocalDateTime watchedAt,
        Integer watchSeconds) {
}
