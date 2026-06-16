package org.streamhub.api.v1.statistics.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A content item in the top-by-views ranking. */
@Getter
@Setter
@NoArgsConstructor
public class TopContentItem {
    private Long id;
    private String title;
    private long viewCount;
    private String channelName;
}
