package org.streamhub.api.v1.pub.me.notification;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.streamhub.api.base.jwt.MemberTokenResolver;
import org.streamhub.api.base.response.ResInfinityList;
import org.streamhub.api.base.response.ResultDTO;
import org.streamhub.api.v1.pub.me.notification.dto.NotificationItem;

/**
 * Member notification-center endpoints under the public ({@code /pub/**}, permitAll) namespace.
 * The member is resolved from the Bearer member token via the shared {@link MemberTokenResolver}
 * (a missing/invalid member token is a 401).
 *
 * <p>Notifications are broadcast (every member sees the same sent messages), but read state is
 * per-member via the {@code NOTIFICATION_READ} overlay: the list carries each member's read flag,
 * {@code /unread-count} drives the badge, and {@code /{id}/read} · {@code /read-all} record reads.
 */
@Tag(name = "Member Notifications", description = "사용자 사이트 알림센터 (회원): 알림 목록 / 안읽음 수 / 읽음 / 모두 읽음")
@RestController
@RequestMapping("/pub/v1/me/notifications")
public class MemberNotificationController {

    private final MemberNotificationService notificationService;
    private final MemberTokenResolver memberTokenResolver;

    public MemberNotificationController(MemberNotificationService notificationService,
                                        MemberTokenResolver memberTokenResolver) {
        this.notificationService = notificationService;
        this.memberTokenResolver = memberTokenResolver;
    }

    @Operation(summary = "내 알림 목록",
            description = "최근 발송된 알림을 최신순으로 페이징해 반환한다. 각 항목의 read는 회원별 읽음 상태다.")
    @GetMapping
    public ResultDTO<ResInfinityList<NotificationItem>> notifications(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "pageNumber", defaultValue = "0") int pageNumber,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        Long memberId = memberTokenResolver.resolve(authorization);
        return ResultDTO.ok(notificationService.notifications(memberId, pageNumber, pageSize));
    }

    @Operation(summary = "안읽은 알림 수", description = "회원이 아직 읽지 않은 알림 개수(배지용)를 반환한다.")
    @GetMapping("/unread-count")
    public ResultDTO<Long> unreadCount(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Long memberId = memberTokenResolver.resolve(authorization);
        return ResultDTO.ok(notificationService.unreadCount(memberId));
    }

    @Operation(summary = "알림 읽음", description = "알림 1건을 회원의 읽음으로 기록한다(멱등). 발송된 알림이 아니면 404.")
    @PostMapping("/{id}/read")
    public ResultDTO<Void> markRead(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id) {
        Long memberId = memberTokenResolver.resolve(authorization);
        notificationService.markRead(memberId, id);
        return ResultDTO.ok();
    }

    @Operation(summary = "알림 모두 읽음", description = "회원의 모든 알림을 읽음으로 기록한다.")
    @PostMapping("/read-all")
    public ResultDTO<Void> markAllRead(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Long memberId = memberTokenResolver.resolve(authorization);
        notificationService.markAllRead(memberId);
        return ResultDTO.ok();
    }
}
