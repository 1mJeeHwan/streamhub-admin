package org.streamhub.audit;

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
 * Persisted audit-log entry owned by this service. Lives in this service's own schema
 * ({@code streamhub_audit}, DB-per-service) — the monolith reaches it through the
 * {@code /v1/action-logs} read API, never by touching the table. See docs/msa-split.md.
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

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "target_type", length = 30)
    private String targetType;

    @Column(name = "target_id", length = 100)
    private String targetId;

    @Column(name = "detail", length = 500)
    private String detail;

    @Column(name = "ip", length = 45)
    private String ip;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private ActionLog(Long adminId, String adminName, String action, String targetType,
                      String targetId, String detail, String ip, LocalDateTime createdAt) {
        this.adminId = adminId;
        this.adminName = adminName;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.detail = detail;
        this.ip = ip;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }
}
