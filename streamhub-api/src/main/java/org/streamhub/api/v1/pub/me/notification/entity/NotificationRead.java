package org.streamhub.api.v1.pub.me.notification.entity;

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
 * A per-member "read" marker over a broadcast {@code NOTIFICATION_LOG} row. The broadcast send-log
 * itself has no per-member state, so this overlay table records which member has read which
 * notification. The {@code (member_id, notification_id)} unique constraint makes mark-read
 * idempotent (a second read of the same notification is a silent no-op).
 */
@Entity
@Table(name = "NOTIFICATION_READ",
        uniqueConstraints = @UniqueConstraint(name = "uk_notification_read_member_notification",
                columnNames = {"member_id", "notification_id"}),
        indexes = {
                @Index(name = "idx_notification_read_member", columnList = "member_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationRead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    /** Id of the {@code NOTIFICATION_LOG} row that was read by this member. */
    @Column(name = "notification_id", nullable = false)
    private Long notificationId;

    @Column(name = "read_at", nullable = false)
    private LocalDateTime readAt;

    @Builder
    private NotificationRead(Long memberId, Long notificationId, LocalDateTime readAt) {
        this.memberId = memberId;
        this.notificationId = notificationId;
        this.readAt = readAt != null ? readAt : LocalDateTime.now();
    }
}
