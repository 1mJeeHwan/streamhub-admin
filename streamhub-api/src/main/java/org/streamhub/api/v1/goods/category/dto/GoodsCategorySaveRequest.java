package org.streamhub.api.v1.goods.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Create/update input for a goods category. {@code parentId} is null for a root node;
 * {@code depth} is derived by the service. On update only {@code name}, {@code sortOrder}
 * and {@code useYn} are applied.
 */
@Getter
@Setter
@NoArgsConstructor
public class GoodsCategorySaveRequest {
    private Long parentId;

    @NotBlank(message = "카테고리명을 입력하세요")
    @Size(max = 100, message = "카테고리명은 100자 이내여야 합니다")
    private String name;

    @PositiveOrZero(message = "정렬순서는 0 이상이어야 합니다")
    private Integer sortOrder;

    @Size(max = 1, message = "사용여부는 Y 또는 N 한 글자여야 합니다")
    private String useYn;
}
