package org.streamhub.api.v1.goods.review;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.streamhub.api.base.response.ResultDTO;
import org.streamhub.api.base.security.AdminPrincipal;
import org.streamhub.api.v1.goods.review.dto.GoodsReviewDisplayRequest;
import org.streamhub.api.v1.goods.review.dto.GoodsReviewDto;
import org.streamhub.api.v1.goods.review.dto.GoodsReviewRatingRequest;
import org.streamhub.api.v1.goods.review.dto.GoodsReviewSearchRequest;

/** Goods review management endpoints (SYSTEM or CHURCH_MANAGER). */
@Tag(name = "GoodsReview", description = "굿즈 상품후기 관리")
@RestController
@RequestMapping("/v1/goods-review")
@PreAuthorize("hasAuthority('goods:read')") // class default = read; mutations require goods:write
public class GoodsReviewController {

    private final GoodsReviewService goodsReviewService;

    public GoodsReviewController(GoodsReviewService goodsReviewService) {
        this.goodsReviewService = goodsReviewService;
    }

    @Operation(summary = "상품후기 목록", description = "관리자용 상품후기 목록(최신순, 노출여부 선택 필터).")
    @PostMapping("/list")
    public ResultDTO<List<GoodsReviewDto>> list(@RequestBody(required = false) GoodsReviewSearchRequest request,
            @AuthenticationPrincipal AdminPrincipal principal) {
        return ResultDTO.ok(goodsReviewService.list(request, principal));
    }

    @Operation(summary = "상품후기 노출여부 변경")
    @PreAuthorize("hasAuthority('goods:write')")
    @PutMapping("/{id}/display")
    public ResultDTO<GoodsReviewDto> display(@PathVariable Long id,
            @Valid @RequestBody GoodsReviewDisplayRequest request,
            @AuthenticationPrincipal AdminPrincipal principal) {
        return ResultDTO.ok(goodsReviewService.changeDisplay(id, request, principal));
    }

    @Operation(summary = "상품후기 별점 수정")
    @PreAuthorize("hasAuthority('goods:write')")
    @PutMapping("/{id}/rating")
    public ResultDTO<GoodsReviewDto> rating(@PathVariable Long id,
            @Valid @RequestBody GoodsReviewRatingRequest request,
            @AuthenticationPrincipal AdminPrincipal principal) {
        return ResultDTO.ok(goodsReviewService.changeRating(id, request, principal));
    }

    @Operation(summary = "상품후기 삭제")
    @PreAuthorize("hasAuthority('goods:write')")
    @DeleteMapping("/{id}")
    public ResultDTO<Void> delete(@PathVariable Long id,
            @AuthenticationPrincipal AdminPrincipal principal) {
        goodsReviewService.delete(id, principal);
        return ResultDTO.ok();
    }
}
