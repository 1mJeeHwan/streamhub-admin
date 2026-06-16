package org.streamhub.api.v1.actionlog.entity;

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

/**
 * A persisted audit-log entry: which operator did what, to which target, when.
 * Written by the SQS consumer after an action is published.
 */
@Entity
@Table(name = "ACTION_LOG", indexes = {
        @Index(name = "idx_actionlog_created", columnList = "created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_id")
    private Long adminId;

    @Column(name = "admin_name", length = 50)
    private String adminName;

    /** Action code, e.g. LOGIN, MEMBER_APPROVE, CONTENT_DELETE. */
    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "target_type", length = 30)
    private String targetType;

    @Column(name = "target_id", length = 100)
    private String targetId;

    @Column(name = "detail", length = 500)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private ActionLog(Long adminId, String adminName, String action, String targetType,
                      String targetId, String detail, LocalDateTime createdAt) {
        this.adminId = adminId;
        this.adminName = adminName;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.detail = detail;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }
}
