package org.streamhub.api.v1.pub.me.notification;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.streamhub.api.v1.pub.me.notification.entity.NotificationRead;

/** Read-marker repository — the per-member read overlay over broadcast notifications. */
public interface NotificationReadRepository extends JpaRepository<NotificationRead, Long> {

    /** True when the member has already read this notification (mark-read idempotency guard). */
    boolean existsByMemberIdAndNotificationId(Long memberId, Long notificationId);

    /** How many notifications this member has read (drives the unread count). */
    long countByMemberId(Long memberId);

    /** Of the given notification ids, those this member has already read (to flag a list page). */
    @Query("select r.notificationId from NotificationRead r "
            + "where r.memberId = :memberId and r.notificationId in :ids")
    List<Long> findReadIds(@Param("memberId") Long memberId, @Param("ids") Collection<Long> ids);

    /** All notification ids this member has read (used to compute the unread set on read-all). */
    @Query("select r.notificationId from NotificationRead r where r.memberId = :memberId")
    List<Long> findAllReadIds(@Param("memberId") Long memberId);
}
