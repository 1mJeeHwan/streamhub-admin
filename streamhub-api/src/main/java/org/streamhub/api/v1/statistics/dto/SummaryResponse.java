package org.streamhub.api.v1.statistics.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Dashboard summary cards. Cached in Redis. */
@Getter
@Setter
@NoArgsConstructor
public class SummaryResponse {
    private long totalMembers;
    private long newMembers7d;
    private long totalViews;
    private long totalContents;
}
