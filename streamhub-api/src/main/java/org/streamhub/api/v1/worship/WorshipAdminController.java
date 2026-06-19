package org.streamhub.api.v1.worship;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.streamhub.api.base.response.ResInfinityList;
import org.streamhub.api.base.response.ResultDTO;
import org.streamhub.api.base.security.AdminPrincipal;
import org.streamhub.api.v1.worship.dto.WorshipRegistrationDetail;
import org.streamhub.api.v1.worship.dto.WorshipRegistrationListItem;
import org.streamhub.api.v1.worship.dto.WorshipSearchRequest;
import org.streamhub.api.v1.worship.dto.WorshipStatusChangeRequest;

/**
 * Worship/new-family registration management endpoints (SYSTEM or CHURCH_MANAGER).
 */
@Tag(name = "Worship", description = "예배·새가족 신청 관리 (데모/테스트 모드, 실알림 미발송)")
@RestController
@RequestMapping("/v1/worship")
@PreAuthorize("hasAnyAuthority(T(org.streamhub.api.base.security.AuthoritiesConstants).SYSTEM, "
        + "T(org.streamhub.api.base.security.AuthoritiesConstants).CHURCH_MANAGER)")
public class WorshipAdminController {

    private final WorshipService worshipService;

    public WorshipAdminController(WorshipService worshipService) {
        this.worshipService = worshipService;
    }

    @Operation(summary = "신청 목록", description = "검색/상태/교회/기간 필터 + 페이지네이션된 신청 목록.")
    @PostMapping("/list")
    public ResultDTO<ResInfinityList<WorshipRegistrationListItem>> list(
            @RequestBody WorshipSearchRequest request,
            @AuthenticationPrincipal AdminPrincipal principal) {
        return ResultDTO.ok(worshipService.list(request, principal));
    }

    @Operation(summary = "신청 상세", description = "신청 정보 + 가족 행.")
    @GetMapping("/{id}")
    public ResultDTO<WorshipRegistrationDetail> detail(
            @PathVariable Long id, @AuthenticationPrincipal AdminPrincipal principal) {
        return ResultDTO.ok(worshipService.getDetail(id, principal));
    }

    @Operation(summary = "신청 상태 변경",
            description = "상태머신 전이(RECEIVED→CONTACTED→COMPLETED, 분기 CANCELED) + 메모 갱신.")
    @PatchMapping("/{id}/status")
    public ResultDTO<WorshipRegistrationDetail> changeStatus(
            @PathVariable Long id, @Valid @RequestBody WorshipStatusChangeRequest request,
            @AuthenticationPrincipal AdminPrincipal principal) {
        return ResultDTO.ok(worshipService.changeStatus(id, request, principal));
    }
}
