package org.streamhub.api.v1.notification.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.streamhub.api.v1.notification.entity.NotificationRecipient;

/** JPA repository for {@link NotificationRecipient} (targeted-notification fan-out rows). */
public interface NotificationRecipientRepository extends JpaRepository<NotificationRecipient, Long> {

    /** Recipient member ids for a notification (empty ⇒ BROADCAST). */
    @Query("SELECT r.memberId FROM NotificationRecipient r WHERE r.notificationId = :notificationId")
    List<Long> findMemberIdsByNotificationId(@Param("notificationId") Long notificationId);
}
