package org.streamhub.api.v1.statistics.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Aggregated watch time per channel. */
@Getter
@Setter
@NoArgsConstructor
public class ChannelWatchItem {
    private String channelName;
    private long totalSeconds;
}
