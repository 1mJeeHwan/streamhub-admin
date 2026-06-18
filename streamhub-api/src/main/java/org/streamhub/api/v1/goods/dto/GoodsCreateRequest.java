package org.streamhub.api.v1.goods.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.streamhub.api.v1.goods.entity.GoodsStatus;

/**
 * Create/update-goods payload. {@code thumbnailKey} and image {@code s3Key}s come from
 * prior /upload calls. {@code options}/{@code images} are dynamic rows (replace-on-save).
 */
public record GoodsCreateRequest(
        @NotNull(message = "분류는 필수입니다") Long categoryId,
        @NotBlank(message = "상품명을 입력하세요") @Size(max = 200, message = "상품명은 200자 이내여야 합니다") String name,
        @NotBlank(message = "상품코드를 입력하세요") @Size(max = 100, message = "상품코드는 100자 이내여야 합니다") String code,
        String description,
        @NotNull(message = "판매가는 필수입니다") @Min(value = 0, message = "판매가는 0 이상이어야 합니다") Long price,
        @Min(value = 0, message = "정가는 0 이상이어야 합니다") Long listPrice,
        @Min(value = 0, message = "재고는 0 이상이어야 합니다") Integer stock,
        @Min(value = 0, message = "알림수량은 0 이상이어야 합니다") Integer notiQty,
        String soldOut,
        String useYn,
        GoodsStatus status,
        String thumbnailKey,
        List<String> badges,
        List<GoodsOptionDto> options,
        List<GoodsImageDto> images) {
}
