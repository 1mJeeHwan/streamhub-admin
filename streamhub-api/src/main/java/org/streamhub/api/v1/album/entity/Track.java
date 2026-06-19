package org.streamhub.api.v1.album.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A track of an {@link Album} (1:N). The 30-second preview window is described by
 * {@code previewStartSec}/{@code previewLengthSec}; the frontend mini-player enforces
 * the cutoff. Replaced via delete-then-reinsert.
 */
@Entity
@Table(name = "TRACK", indexes = {
        @Index(name = "idx_track_album", columnList = "album_id"),
        @Index(name = "uk_track_album_no", columnList = "album_id, track_no", unique = true)
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Track {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → ALBUM. */
    @Column(name = "album_id", nullable = false)
    private Long albumId;

    /** Track number (from 1). */
    @Column(name = "track_no", nullable = false)
    private Integer trackNo;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** Full track length (seconds). */
    @Column(name = "duration_sec")
    private Integer durationSec;

    /** 30-second preview audio URL (SoundHelix sample). */
    @Column(name = "preview_url", length = 500)
    private String previewUrl;

    /** Preview start offset (seconds). */
    @Column(name = "preview_start_sec", nullable = false)
    private Integer previewStartSec;

    /** Preview length (seconds, default 30). */
    @Column(name = "preview_length_sec", nullable = false)
    private Integer previewLengthSec;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 12)
    private MusicSource source;

    /** External track id (currently null). */
    @Column(name = "external_id", length = 80)
    private String externalId;

    // --- Encrypted full-track (HLS + AES-128) ----------------------------------------------------

    /**
     * S3 key prefix where this track's HLS assets live ({@code hls/track-{id}/} — the AES-128
     * encrypted {@code .ts} segments + {@code index.m3u8}). {@code null} until packaged. Segments
     * are CDN-cacheable (they are encrypted); only the key is access-gated.
     */
    @Column(name = "hls_prefix", length = 200)
    private String hlsPrefix;

    /** FK → {@code TRACK_HLS_KEY}: the server-only AES-128 key, served only to purchasers. */
    @Column(name = "hls_key_id")
    private Long hlsKeyId;

    /** True once the encrypted full track has been packaged and is playable by purchasers. */
    @Column(name = "has_full_track", nullable = false)
    private boolean hasFullTrack;

    /**
     * S3 key prefix for this track's <b>public, unencrypted</b> 30-second preview HLS
     * ({@code hls/preview/track-{id}/}). {@code null} until packaged — when null the frontend falls
     * back to the legacy direct {@code previewUrl}. No AES key: the preview is free, so the playlist
     * and segments are freely cacheable.
     */
    @Column(name = "preview_hls_prefix", length = 200)
    private String previewHlsPrefix;

    @Builder
    private Track(Long albumId, Integer trackNo, String title, Integer durationSec, String previewUrl,
                  Integer previewStartSec, Integer previewLengthSec, MusicSource source,
                  String externalId) {
        this.albumId = albumId;
        this.trackNo = trackNo;
        this.title = title;
        this.durationSec = durationSec;
        this.previewUrl = previewUrl;
        this.previewStartSec = previewStartSec != null ? previewStartSec : 0;
        this.previewLengthSec = previewLengthSec != null ? previewLengthSec : 30;
        this.source = source != null ? source : MusicSource.SEED;
        this.externalId = externalId;
    }

    /** Marks this track as having a packaged encrypted full-track HLS asset. */
    public void attachHls(String hlsPrefix, Long hlsKeyId, Integer durationSec) {
        this.hlsPrefix = hlsPrefix;
        this.hlsKeyId = hlsKeyId;
        this.hasFullTrack = true;
        if (durationSec != null) {
            this.durationSec = durationSec;
        }
    }

    /** Records the S3 prefix of this track's packaged public preview HLS. */
    public void attachPreviewHls(String previewHlsPrefix) {
        this.previewHlsPrefix = previewHlsPrefix;
    }
}
