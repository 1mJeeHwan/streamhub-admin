package org.streamhub.api.v1.inquiry;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.streamhub.api.base.response.ResultDTO;
import org.streamhub.api.base.security.AdminPrincipal;
import org.streamhub.api.v1.inquiry.dto.InquiryAnswerRequest;
import org.streamhub.api.v1.inquiry.dto.InquiryDto;
import org.streamhub.api.v1.inquiry.dto.InquirySearchRequest;

/**
 * 1:1 customer inquiry (고객 문의) management endpoints (SYSTEM or CHURCH_MANAGER).
 */
@Tag(name = "Inquiry", description = "1:1 고객 문의 관리")
@RestController
@RequestMapping("/v1/inquiry")
@PreAuthorize("hasAuthority('inquiry:read')") // class default = read; mutations require inquiry:write
public class InquiryController {

    private final InquiryService inquiryService;

    public InquiryController(InquiryService inquiryService) {
        this.inquiryService = inquiryService;
    }

    @Operation(summary = "문의 목록", description = "관리자용 문의 목록(최신순). 상태/카테고리 필터 선택, 기본은 미답변(OPEN) 우선.")
    @PostMapping("/list")
    public ResultDTO<List<InquiryDto>> list(@RequestBody(required = false) InquirySearchRequest request,
            @AuthenticationPrincipal AdminPrincipal principal) {
        return ResultDTO.ok(inquiryService.list(request, principal));
    }

    @Operation(summary = "문의 상세")
    @GetMapping("/{id}")
    public ResultDTO<InquiryDto> detail(@PathVariable Long id,
            @AuthenticationPrincipal AdminPrincipal principal) {
        return ResultDTO.ok(inquiryService.detail(id, principal));
    }

    @Operation(summary = "문의 답변", description = "답변 등록 후 상태를 ANSWERED로 변경한다.")
    @PreAuthorize("hasAuthority('inquiry:write')")
    @PutMapping("/{id}/answer")
    public ResultDTO<InquiryDto> answer(@PathVariable Long id, @Valid @RequestBody InquiryAnswerRequest request,
            @AuthenticationPrincipal AdminPrincipal principal) {
        return ResultDTO.ok(inquiryService.answer(id, request, principal));
    }

    @Operation(summary = "문의 종료", description = "상태를 CLOSED로 변경한다.")
    @PreAuthorize("hasAuthority('inquiry:write')")
    @PutMapping("/{id}/close")
    public ResultDTO<InquiryDto> close(@PathVariable Long id,
            @AuthenticationPrincipal AdminPrincipal principal) {
        return ResultDTO.ok(inquiryService.close(id, principal));
    }

    @Operation(summary = "문의 삭제")
    @PreAuthorize("hasAuthority('inquiry:write')")
    @DeleteMapping("/{id}")
    public ResultDTO<Void> delete(@PathVariable Long id,
            @AuthenticationPrincipal AdminPrincipal principal) {
        inquiryService.delete(id, principal);
        return ResultDTO.ok();
    }
}
