package org.streamhub.api.v1.statistics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** A single content-watch event; the source data for dashboard aggregations. */
@Entity
@Table(name = "WATCH_HISTORY", indexes = {
        @Index(name = "idx_watch_content", columnList = "content_id"),
        @Index(name = "idx_watch_member", columnList = "member_id"),
        @Index(name = "idx_watch_watched_at", columnList = "watched_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WatchHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "content_id", nullable = false)
    private Long contentId;

    @Column(name = "watched_at", nullable = false)
    private LocalDateTime watchedAt;

    @Column(name = "watch_seconds", nullable = false)
    private Integer watchSeconds;

    @Builder
    private WatchHistory(Long memberId, Long contentId, LocalDateTime watchedAt, Integer watchSeconds) {
        this.memberId = memberId;
        this.contentId = contentId;
        this.watchedAt = watchedAt;
        this.watchSeconds = watchSeconds;
    }
}
