package org.streamhub.api.v1.pub.me.notification.dto;

import java.time.LocalDateTime;

/**
 * One notification-center item exposed to a logged-in member.
 *
 * <p>The backing {@code NOTIFICATION_LOG} is a broadcast send-log shown to every member; {@code read}
 * is this member's read state, layered on via the {@code NOTIFICATION_READ} overlay.
 *
 * @param id        notification log id
 * @param title     notification title
 * @param body      notification body (maps to the log's {@code content}; may be null)
 * @param read      whether this member has read the notification
 * @param createdAt when the notification was created
 */
public record NotificationItem(
        Long id,
        String title,
        String body,
        boolean read,
        LocalDateTime createdAt) {
}
