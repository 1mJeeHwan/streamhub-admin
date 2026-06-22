package org.streamhub.api.v1.actionlog;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.streamhub.api.base.response.ResInfinityList;
import org.streamhub.api.base.response.ResultDTO;
import org.streamhub.api.v1.actionlog.dto.ActionLogItem;
import org.streamhub.api.v1.actionlog.dto.ActionLogSearchRequest;

/**
 * Audit-log viewing (SYSTEM only — operational/sensitive data).
 */
@Tag(name = "ActionLog", description = "감사 로그")
@RestController
@RequestMapping("/v1/action-log")
@PreAuthorize("hasAuthority('actionlog:read')") // SYSTEM-only resource; read-only controller (POST /list is search)
public class ActionLogController {

    private final ActionLogReader actionLogReader;

    public ActionLogController(ActionLogReader actionLogReader) {
        this.actionLogReader = actionLogReader;
    }

    @Operation(summary = "감사 로그 목록", description = "관리자 액션 기록을 검색/페이지네이션 조회한다.")
    @PostMapping("/list")
    public ResultDTO<ResInfinityList<ActionLogItem>> list(@RequestBody ActionLogSearchRequest request) {
        return ResultDTO.ok(actionLogReader.list(request));
    }
}
