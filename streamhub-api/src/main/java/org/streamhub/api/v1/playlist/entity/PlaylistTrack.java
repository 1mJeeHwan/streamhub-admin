package org.streamhub.api.v1.playlist.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One ordered entry in a playlist, referencing an existing album track by id. */
@Entity
@Table(name = "PLAYLIST_TRACK", indexes = {
        @Index(name = "idx_playlist_track_playlist", columnList = "playlist_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaylistTrack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "playlist_id", nullable = false)
    private Long playlistId;

    @Column(name = "track_id", nullable = false)
    private Long trackId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Builder
    private PlaylistTrack(Long playlistId, Long trackId, int sortOrder) {
        this.playlistId = playlistId;
        this.trackId = trackId;
        this.sortOrder = sortOrder;
    }
}
