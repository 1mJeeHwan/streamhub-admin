package org.streamhub.audit;

import java.time.LocalDateTime;

/**
 * One audit row as returned by this service's read API. Field names mirror the monolith's
 * {@code ActionLogItem} so the cross-service JSON maps straight back without translation.
 */
public record ActionLogView(
        Long id,
        Long adminId,
        String adminName,
        String action,
        String targetType,
        String targetId,
        String detail,
        String ip,
        LocalDateTime createdAt) {

    static ActionLogView from(ActionLog entity) {
        return new ActionLogView(
                entity.getId(),
                entity.getAdminId(),
                entity.getAdminName(),
                entity.getAction(),
                entity.getTargetType(),
                entity.getTargetId(),
                entity.getDetail(),
                entity.getIp(),
                entity.getCreatedAt());
    }
}
