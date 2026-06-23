package org.streamhub.api.v1.playlist.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

/** Playlist with its ordered tracks (admin detail + public detail). */
@Getter
@Builder
public class PlaylistDetail {
    private final Long id;
    private final String title;
    private final String description;
    private final String coverKey;
    private final String coverUrl;
    private final int sortOrder;
    private final String useYn;
    private final List<PlaylistTrackItem> tracks;
}
