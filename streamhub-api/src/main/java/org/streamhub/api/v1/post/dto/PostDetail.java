package org.streamhub.api.v1.post.dto;

import java.time.LocalDateTime;
import org.streamhub.api.v1.post.entity.Post;

/** Full post detail for the public detail page. */
public record PostDetail(
        Long id,
        String title,
        String body,
        String thumbnailKey,
        String thumbnailUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /** Builds a detail from the entity; {@code thumbnailUrl} is resolved by the service. */
    public static PostDetail of(Post post, String thumbnailUrl) {
        return new PostDetail(
                post.getId(), post.getTitle(), post.getBody(),
                post.getThumbnailKey(), thumbnailUrl,
                post.getCreatedAt(), post.getUpdatedAt());
    }
}
