package org.streamhub.api.v1.statistics.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One point on the daily signup trend. */
@Getter
@Setter
@NoArgsConstructor
public class TrendPoint {
    private String date; // yyyy-MM-dd
    private long count;
}
