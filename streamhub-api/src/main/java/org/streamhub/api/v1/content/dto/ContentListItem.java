package org.streamhub.api.v1.content.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.streamhub.api.v1.content.entity.ContentStatus;
import org.streamhub.api.v1.content.entity.ContentType;

/** One row of the content list, joined with channel/church names and hashtags. */
@Getter
@Setter
@NoArgsConstructor
public class ContentListItem {
    private Long id;
    private String title;
    private ContentType type;
    private ContentStatus status;
    private Long channelId;
    private String channelName;
    private String churchName;
    private String thumbnailKey;
    private String thumbnailUrl; // filled by the service from thumbnailKey
    private Long viewCount;
    private Integer durationSec;
    private String hashtags; // comma-joined (GROUP_CONCAT)
    private LocalDateTime createdAt;
}
