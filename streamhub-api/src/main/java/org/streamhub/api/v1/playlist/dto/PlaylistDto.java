package org.streamhub.api.v1.playlist.dto;

import lombok.Builder;
import lombok.Getter;

/** Playlist summary row (list views). {@code coverUrl} is resolved from {@code coverKey} by the service. */
@Getter
@Builder
public class PlaylistDto {
    private final Long id;
    private final String title;
    private final String description;
    private final String coverKey;
    private final String coverUrl;
    private final int sortOrder;
    private final String useYn;
    private final long trackCount;
}
