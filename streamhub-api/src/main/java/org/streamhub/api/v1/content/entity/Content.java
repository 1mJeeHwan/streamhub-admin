package org.streamhub.api.v1.content.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A media content item (video or sound) belonging to a {@link Channel}. */
@Entity
@Table(name = "CONTENT", indexes = {
        @Index(name = "idx_content_channel", columnList = "channel_id"),
        @Index(name = "idx_content_status_created", columnList = "status, created_at"),
        @Index(name = "idx_content_type", columnList = "type")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Content {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "channel_id", nullable = false)
    private Long channelId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private ContentType type;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "thumbnail_key", length = 300)
    private String thumbnailKey;

    @Column(name = "media_url", length = 500)
    private String mediaUrl;

    /**
     * S3 key prefix for this content's <b>public, unencrypted</b> HLS stream
     * ({@code hls/content/{id}/}). {@code null} until packaged — when null the frontend falls back
     * to the legacy direct {@code mediaUrl}. Audio content (음원) is free, so no AES key/gate.
     */
    @Column(name = "hls_prefix", length = 200)
    private String hlsPrefix;

    @Column(name = "duration_sec")
    private Integer durationSec;

    @Column(name = "view_count", nullable = false)
    private Long viewCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private ContentStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private Content(Long channelId, ContentType type, String title, String description,
                    String thumbnailKey, String mediaUrl, Integer durationSec,
                    ContentStatus status, Long viewCount, LocalDateTime createdAt) {
        this.channelId = channelId;
        this.type = type;
        this.title = title;
        this.description = description;
        this.thumbnailKey = thumbnailKey;
        this.mediaUrl = mediaUrl;
        this.durationSec = durationSec;
        this.status = status;
        this.viewCount = viewCount != null ? viewCount : 0L;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    /** Updates editable fields. */
    public void update(String title, String description, ContentType type, Long channelId,
                       String mediaUrl, Integer durationSec, String thumbnailKey, ContentStatus status) {
        this.title = title;
        this.description = description;
        this.type = type;
        this.channelId = channelId;
        this.mediaUrl = mediaUrl;
        this.durationSec = durationSec;
        this.thumbnailKey = thumbnailKey;
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    /** Records the S3 prefix of this content's packaged public HLS stream. */
    public void attachHls(String hlsPrefix) {
        this.hlsPrefix = hlsPrefix;
    }
}
