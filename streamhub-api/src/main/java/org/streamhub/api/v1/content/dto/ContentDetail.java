package org.streamhub.api.v1.content.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.streamhub.api.v1.content.entity.ContentStatus;
import org.streamhub.api.v1.content.entity.ContentType;

/** Full content detail. Base fields from MyBatis; hashtags/files/urls filled by the service. */
@Getter
@Setter
@NoArgsConstructor
public class ContentDetail {
    private Long id;
    private String title;
    private String description;
    private ContentType type;
    private ContentStatus status;
    private Long channelId;
    private String channelName;
    private String churchName;
    private String mediaUrl;
    private String thumbnailKey;
    private String thumbnailUrl;
    private Integer durationSec;
    private Long viewCount;
    private List<String> hashtags;
    private List<ContentFileDto> files;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
