package org.streamhub.api.v1.pub.me.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A member's "찜" (favorite) on a single album track — the playlist seam for the user site.
 * The {@code (member_id, track_id)} unique constraint makes the add operation idempotent: a second
 * favorite of the same track is silently a no-op. {@code albumId} is denormalised at add time so the
 * favorites feed can join album metadata without re-resolving each track.
 */
@Entity
@Table(name = "TRACK_FAVORITE",
        uniqueConstraints = @UniqueConstraint(name = "uk_track_favorite_member_track",
                columnNames = {"member_id", "track_id"}),
        indexes = {
                @Index(name = "idx_track_favorite_member", columnList = "member_id"),
                @Index(name = "idx_track_favorite_track", columnList = "track_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrackFavorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "track_id", nullable = false)
    private Long trackId;

    /** FK → ALBUM, denormalised from the track at add time. */
    @Column(name = "album_id", nullable = false)
    private Long albumId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private TrackFavorite(Long memberId, Long trackId, Long albumId, LocalDateTime createdAt) {
        this.memberId = memberId;
        this.trackId = trackId;
        this.albumId = albumId;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }
}
