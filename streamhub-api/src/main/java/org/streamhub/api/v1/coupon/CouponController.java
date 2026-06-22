package org.streamhub.api.v1.coupon;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.streamhub.api.base.response.ResultDTO;
import org.streamhub.api.v1.coupon.dto.CouponDto;
import org.streamhub.api.v1.coupon.dto.CouponRedemptionItem;
import org.streamhub.api.v1.coupon.dto.CouponSearchRequest;

/**
 * Discount-coupon management endpoints (SYSTEM or CHURCH_MANAGER).
 */
@Tag(name = "Coupon", description = "할인 쿠폰 관리")
@RestController
@RequestMapping("/v1/coupon")
@PreAuthorize("hasAuthority('coupon:read')") // class default = read; mutations require coupon:write
public class CouponController {

    private final CouponService couponService;

    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @Operation(summary = "쿠폰 목록", description = "관리자용 쿠폰 목록(최신순). 사용여부/키워드 필터 가능.")
    @PostMapping("/list")
    public ResultDTO<List<CouponDto>> list(@RequestBody(required = false) CouponSearchRequest request) {
        return ResultDTO.ok(couponService.list(request));
    }

    @Operation(summary = "쿠폰 상세")
    @GetMapping("/{id}")
    public ResultDTO<CouponDto> detail(@PathVariable Long id) {
        return ResultDTO.ok(couponService.detail(id));
    }

    @Operation(summary = "쿠폰 사용 내역", description = "해당 쿠폰을 사용한 회원/시각 목록(최신순).")
    @GetMapping("/{id}/redemptions")
    public ResultDTO<List<CouponRedemptionItem>> redemptions(@PathVariable Long id) {
        return ResultDTO.ok(couponService.redemptions(id));
    }

    @Operation(summary = "쿠폰 등록")
    @PreAuthorize("hasAuthority('coupon:write')")
    @PostMapping
    public ResultDTO<CouponDto> create(@Valid @RequestBody CouponDto request) {
        return ResultDTO.ok(couponService.create(request));
    }

    @Operation(summary = "쿠폰 수정")
    @PreAuthorize("hasAuthority('coupon:write')")
    @PutMapping("/{id}")
    public ResultDTO<CouponDto> update(@PathVariable Long id, @Valid @RequestBody CouponDto request) {
        return ResultDTO.ok(couponService.update(id, request));
    }

    @Operation(summary = "쿠폰 삭제")
    @PreAuthorize("hasAuthority('coupon:write')")
    @DeleteMapping("/{id}")
    public ResultDTO<Void> delete(@PathVariable Long id) {
        couponService.delete(id);
        return ResultDTO.ok();
    }
}
