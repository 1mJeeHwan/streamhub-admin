package org.streamhub.api.v1.pub.me.notification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResInfinityList;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.v1.notification.entity.NotificationLog;
import org.streamhub.api.v1.notification.entity.NotificationStatus;
import org.streamhub.api.v1.pub.me.notification.dto.NotificationItem;
import org.streamhub.api.v1.pub.me.notification.entity.NotificationRead;

/**
 * Read service for a logged-in member's notification feed under the public namespace.
 *
 * <p>{@code NOTIFICATION_LOG} is a broadcast send-log (no member targeting), so every member sees
 * the same successfully-sent notifications. Per-member <em>read state</em> is layered on via the
 * {@code NOTIFICATION_READ} overlay: each list row carries this member's read flag, mark-read
 * records a read marker (idempotent), and an unread count drives the badge.
 */
@Slf4j
@Service
public class MemberNotificationService {

    private final MemberNotificationRepository notificationRepository;
    private final NotificationReadRepository readRepository;

    public MemberNotificationService(MemberNotificationRepository notificationRepository,
                                     NotificationReadRepository readRepository) {
        this.notificationRepository = notificationRepository;
        this.readRepository = readRepository;
    }

    /** A page of broadcast notifications (newest first), each flagged with this member's read state. */
    @Transactional(readOnly = true)
    public ResInfinityList<NotificationItem> notifications(Long memberId, int pageNumber, int pageSize) {
        Pageable pageable = PageRequest.of(Math.max(pageNumber, 0), Math.max(pageSize, 1));
        Page<NotificationLog> page =
                notificationRepository.findByStatusOrderByCreatedAtDesc(NotificationStatus.SUCCESS, pageable);

        List<Long> ids = page.getContent().stream().map(NotificationLog::getId).toList();
        Set<Long> readIds = ids.isEmpty() ? Set.of() : Set.copyOf(readRepository.findReadIds(memberId, ids));

        List<NotificationItem> contents = page.getContent().stream()
                .map(log -> new NotificationItem(
                        log.getId(), log.getTitle(), log.getContent(),
                        readIds.contains(log.getId()), log.getCreatedAt()))
                .toList();
        return ResInfinityList.of(contents, page.getTotalElements(), pageable.getPageSize());
    }

    /** Number of successfully-sent notifications this member has not yet read (badge count). */
    @Transactional(readOnly = true)
    public long unreadCount(Long memberId) {
        long total = notificationRepository.countByStatus(NotificationStatus.SUCCESS);
        long read = readRepository.countByMemberId(memberId);
        return Math.max(0, total - read);
    }

    /** Marks one notification read for the member (idempotent). 404 if the id isn't a sent notification. */
    @Transactional
    public void markRead(Long memberId, Long notificationId) {
        if (!notificationRepository.existsByIdAndStatus(notificationId, NotificationStatus.SUCCESS)) {
            throw new ApiException(ResultCode.NOT_FOUND);
        }
        if (readRepository.existsByMemberIdAndNotificationId(memberId, notificationId)) {
            return; // already read — no-op
        }
        readRepository.save(NotificationRead.builder()
                .memberId(memberId)
                .notificationId(notificationId)
                .readAt(LocalDateTime.now())
                .build());
    }

    /** Marks every sent notification read for the member (the "모두 읽음" action). */
    @Transactional
    public void markAllRead(Long memberId) {
        List<Long> allIds = notificationRepository.findIdsByStatus(NotificationStatus.SUCCESS);
        Set<Long> alreadyRead = Set.copyOf(readRepository.findAllReadIds(memberId));
        LocalDateTime now = LocalDateTime.now();
        List<NotificationRead> fresh = allIds.stream()
                .filter(id -> !alreadyRead.contains(id))
                .map(id -> NotificationRead.builder().memberId(memberId).notificationId(id).readAt(now).build())
                .toList();
        if (!fresh.isEmpty()) {
            readRepository.saveAll(fresh);
        }
    }
}
