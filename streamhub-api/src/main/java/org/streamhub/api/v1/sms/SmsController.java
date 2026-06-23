package org.streamhub.api.v1.sms;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.streamhub.api.base.response.ResInfinityList;
import org.streamhub.api.base.response.ResultDTO;
import org.streamhub.api.base.security.AdminPrincipal;
import org.streamhub.api.v1.sms.dto.SmsListItem;
import org.streamhub.api.v1.sms.dto.SmsSearchRequest;
import org.streamhub.api.v1.sms.dto.SmsSendRequest;

/**
 * SMS management endpoints (SYSTEM or CHURCH_MANAGER). All sends are demo/test mode —
 * nothing is actually dispatched (C6).
 */
@Tag(name = "Sms", description = "문자(SMS/LMS) 발송·내역 (데모/테스트 모드)")
@RestController
@RequestMapping("/v1/sms")
@PreAuthorize("hasAuthority('sms:read')") // class default = read; send requires sms:write
public class SmsController {

    private final SmsService smsService;

    public SmsController(SmsService smsService) {
        this.smsService = smsService;
    }

    @Operation(summary = "문자 발송 내역", description = "검색/종류/기간 필터 + 페이지네이션된 발송 내역.")
    @PostMapping("/list")
    public ResultDTO<ResInfinityList<SmsListItem>> list(@RequestBody SmsSearchRequest request,
            @AuthenticationPrincipal AdminPrincipal principal) {
        return ResultDTO.ok(smsService.list(request, principal));
    }

    @Operation(summary = "커스텀 문자 발송", description = "관리자 직접 발송. 실제 발송되지 않으며 로그만 저장된다(테스트).")
    @PreAuthorize("hasAuthority('sms:write')")
    @PostMapping("/send")
    public ResultDTO<SmsListItem> send(@Valid @RequestBody SmsSendRequest request,
            @AuthenticationPrincipal AdminPrincipal principal) {
        return ResultDTO.ok(smsService.send(request, principal));
    }
}
