package org.streamhub.api.v1.member;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.streamhub.api.base.response.ResInfinityList;
import org.streamhub.api.base.response.ResultDTO;
import org.streamhub.api.base.security.AdminPrincipal;
import org.streamhub.api.v1.member.dto.IdListRequest;
import org.streamhub.api.v1.member.dto.MemberDetail;
import org.streamhub.api.v1.member.dto.MemberListItem;
import org.streamhub.api.v1.member.dto.MemberSearchRequest;
import org.streamhub.api.v1.member.dto.MemberUpdateRequest;

/**
 * Member management endpoints. Accessible to SYSTEM and CHURCH_MANAGER operators;
 * the service scopes CHURCH_MANAGER access to their own church.
 */
@Tag(name = "Member", description = "회원관리")
@RestController
@RequestMapping("/v1/member")
@PreAuthorize("hasAnyAuthority(T(org.streamhub.api.base.security.AuthoritiesConstants).SYSTEM, "
        + "T(org.streamhub.api.base.security.AuthoritiesConstants).CHURCH_MANAGER)")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @Operation(summary = "회원 목록", description = "검색/필터/페이지네이션된 회원 목록을 반환한다.")
    @PostMapping("/list")
    public ResultDTO<ResInfinityList<MemberListItem>> list(
            @RequestBody MemberSearchRequest request,
            @AuthenticationPrincipal AdminPrincipal principal) {
        return ResultDTO.ok(memberService.list(request, principal));
    }

    @Operation(summary = "회원 상세")
    @GetMapping("/{id}")
    public ResultDTO<MemberDetail> detail(
            @PathVariable Long id,
            @AuthenticationPrincipal AdminPrincipal principal) {
        return ResultDTO.ok(memberService.getDetail(id, principal));
    }

    @Operation(summary = "회원 수정")
    @PutMapping("/{id}")
    public ResultDTO<MemberDetail> update(
            @PathVariable Long id,
            @Valid @RequestBody MemberUpdateRequest request,
            @AuthenticationPrincipal AdminPrincipal principal) {
        return ResultDTO.ok(memberService.update(id, request, principal));
    }

    @Operation(summary = "회원 일괄 승인", description = "선택한 회원을 CONFIRMED로 변경한다.")
    @PostMapping("/approve")
    public ResultDTO<Integer> approve(
            @Valid @RequestBody IdListRequest request,
            @AuthenticationPrincipal AdminPrincipal principal) {
        return ResultDTO.ok(memberService.approve(request.idList(), principal));
    }

    @Operation(summary = "회원 일괄 거부", description = "선택한 회원을 INACTIVE로 변경한다.")
    @PostMapping("/deny")
    public ResultDTO<Integer> deny(
            @Valid @RequestBody IdListRequest request,
            @AuthenticationPrincipal AdminPrincipal principal) {
        return ResultDTO.ok(memberService.deny(request.idList(), principal));
    }
}
