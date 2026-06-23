package org.streamhub.api.v1.notification;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.streamhub.api.base.response.ResultDTO;
import org.streamhub.api.base.security.AdminPrincipal;
import org.streamhub.api.v1.notification.dto.NotificationLogDto;
import org.streamhub.api.v1.notification.dto.NotificationSearchRequest;
import org.streamhub.api.v1.notification.dto.NotificationSendRequest;
import org.streamhub.api.v1.notification.dto.NotificationSummaryDto;

/**
 * Notification-center send-log endpoints (SYSTEM or CHURCH_MANAGER). Log only — no real
 * SMS/push/email is sent; the dataset is seeded demo data.
 */
@Tag(name = "Notification", description = "알림센터 발송 로그")
@RestController
@RequestMapping("/v1/notification")
@PreAuthorize("hasAuthority('notification:read')") // class default = read; mutations require notification:write
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Operation(summary = "알림 발송",
            description = "알림을 발송한다(로그 기록만). 전체(BROADCAST) 또는 특정 회원(TARGETED, memberIds 필수). 회원은 /pub/v1/me/notifications 피드에서 확인.")
    @PreAuthorize("hasAuthority('notification:write')")
    @PostMapping("/send")
    public ResultDTO<NotificationLogDto> send(@Valid @RequestBody NotificationSendRequest request,
            @AuthenticationPrincipal AdminPrincipal principal) {
        return ResultDTO.ok(notificationService.send(request, principal));
    }

    @Operation(summary = "발송 로그 목록", description = "채널/상태/기간/키워드 필터 적용(최신순).")
    @PostMapping("/list")
    public ResultDTO<List<NotificationLogDto>> list(@RequestBody(required = false) NotificationSearchRequest request) {
        return ResultDTO.ok(notificationService.list(request));
    }

    @Operation(summary = "발송 로그 요약", description = "상태별 합계와 채널별(SMS/PUSH/EMAIL) 건수.")
    @GetMapping("/summary")
    public ResultDTO<NotificationSummaryDto> summary() {
        return ResultDTO.ok(notificationService.summary());
    }

    @Operation(summary = "발송 로그 상세")
    @GetMapping("/{id}")
    public ResultDTO<NotificationLogDto> detail(@PathVariable Long id) {
        return ResultDTO.ok(notificationService.detail(id));
    }

    @Operation(summary = "발송 로그 삭제")
    @PreAuthorize("hasAuthority('notification:write')")
    @DeleteMapping("/{id}")
    public ResultDTO<Void> delete(@PathVariable Long id,
            @AuthenticationPrincipal AdminPrincipal principal) {
        notificationService.delete(id, principal);
        return ResultDTO.ok();
    }
}
