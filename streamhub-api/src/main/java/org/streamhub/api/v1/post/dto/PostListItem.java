package org.streamhub.api.v1.post.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One row of the public post list: a short excerpt and thumbnail, no full body. */
@Getter
@Setter
@NoArgsConstructor
public class PostListItem {
    private Long id;
    private String title;
    private String excerpt; // first chars of body (LEFT(body, n))
    private String thumbnailKey;
    private String thumbnailUrl; // filled by the service from thumbnailKey
    private LocalDateTime createdAt;
}
