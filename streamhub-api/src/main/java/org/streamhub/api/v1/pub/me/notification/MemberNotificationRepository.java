package org.streamhub.api.v1.pub.me.notification;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.streamhub.api.v1.notification.entity.NotificationLog;
import org.streamhub.api.v1.notification.entity.NotificationStatus;

/**
 * Read-side repository for the member notification feed. Kept separate from the admin
 * {@code NotificationLogRepository} so the public {@code /pub/v1/me} feature owns its own query
 * surface. {@code NOTIFICATION_LOG} is a broadcast send-log shown to every member; per-member read
 * state lives in {@link NotificationReadRepository}.
 */
public interface MemberNotificationRepository extends JpaRepository<NotificationLog, Long> {

    /** Most recent successfully-sent broadcast notifications, newest first. */
    Page<NotificationLog> findByStatusOrderByCreatedAtDesc(NotificationStatus status, Pageable pageable);

    /** Total successfully-sent notifications (the denominator for the unread count). */
    long countByStatus(NotificationStatus status);

    /** True when the id is a real successfully-sent notification (mark-read target guard). */
    boolean existsByIdAndStatus(Long id, NotificationStatus status);

    /** All successfully-sent notification ids (used to compute the unread set on read-all). */
    @Query("select n.id from NotificationLog n where n.status = :status")
    List<Long> findIdsByStatus(@Param("status") NotificationStatus status);
}
