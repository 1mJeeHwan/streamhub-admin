package org.streamhub.api.v1.content.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One channel in the public channel directory (GET /pub/v1/channels). Carries the owning
 * church's name and the count of PUBLISHED content of the requested type, so the user site
 * can render a channel-browse carousel ordered by activity.
 */
@Getter
@Setter
@NoArgsConstructor
public class PublicChannelItem {
    private Long id;
    private String name;
    private String churchName;
    private Long contentCount;
}
