package org.streamhub.api.v1.goods.inquiry;

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
import org.streamhub.api.v1.goods.inquiry.dto.GoodsInquiryAnswerRequest;
import org.streamhub.api.v1.goods.inquiry.dto.GoodsInquiryDto;
import org.streamhub.api.v1.goods.inquiry.dto.GoodsInquirySearchRequest;

/** Goods Q&A management endpoints (SYSTEM or CHURCH_MANAGER). */
@Tag(name = "GoodsInquiry", description = "굿즈 상품문의 관리")
@RestController
@RequestMapping("/v1/goods-inquiry")
@PreAuthorize("hasAuthority('goods:read')") // class default = read; mutations require goods:write
public class GoodsInquiryController {

    private final GoodsInquiryService goodsInquiryService;

    public GoodsInquiryController(GoodsInquiryService goodsInquiryService) {
        this.goodsInquiryService = goodsInquiryService;
    }

    @Operation(summary = "상품문의 목록", description = "관리자용 상품문의 목록(최신순, 답변상태 선택 필터).")
    @PostMapping("/list")
    public ResultDTO<List<GoodsInquiryDto>> list(@RequestBody(required = false) GoodsInquirySearchRequest request,
            @AuthenticationPrincipal AdminPrincipal principal) {
        return ResultDTO.ok(goodsInquiryService.list(request, principal));
    }

    @Operation(summary = "상품문의 상세")
    @GetMapping("/{id}")
    public ResultDTO<GoodsInquiryDto> detail(@PathVariable Long id,
            @AuthenticationPrincipal AdminPrincipal principal) {
        return ResultDTO.ok(goodsInquiryService.detail(id, principal));
    }

    @Operation(summary = "상품문의 답변 등록")
    @PreAuthorize("hasAuthority('goods:write')")
    @PutMapping("/{id}/answer")
    public ResultDTO<GoodsInquiryDto> answer(@PathVariable Long id,
            @Valid @RequestBody GoodsInquiryAnswerRequest request,
            @AuthenticationPrincipal AdminPrincipal principal) {
        return ResultDTO.ok(goodsInquiryService.answer(id, request, principal));
    }

    @Operation(summary = "상품문의 삭제")
    @PreAuthorize("hasAuthority('goods:write')")
    @DeleteMapping("/{id}")
    public ResultDTO<Void> delete(@PathVariable Long id,
            @AuthenticationPrincipal AdminPrincipal principal) {
        goodsInquiryService.delete(id, principal);
        return ResultDTO.ok();
    }
}
