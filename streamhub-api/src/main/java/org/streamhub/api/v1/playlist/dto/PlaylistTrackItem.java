package org.streamhub.api.v1.playlist.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * One track in a playlist, carrying everything the player needs to stream it through the existing
 * album track endpoints ({@code albumId}+{@code id} → preview/full HLS). Artist/cover come from the
 * owning album.
 */
@Getter
@Builder
public class PlaylistTrackItem {
    private final Long id; // track id
    private final Long albumId;
    private final Integer trackNo;
    private final String title;
    private final String artist;
    private final String albumTitle;
    private final String coverUrl;
    private final Integer durationSec;
    private final boolean hasFullTrack;
    private final boolean hasPreviewHls;
    private final String previewUrl;
    private final Integer previewStartSec;
    private final Integer previewLengthSec;
}
