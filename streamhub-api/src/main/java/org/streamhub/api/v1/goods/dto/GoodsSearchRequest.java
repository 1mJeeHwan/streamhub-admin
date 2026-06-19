package org.streamhub.api.v1.goods.dto;

import org.streamhub.api.v1.goods.entity.GoodsStatus;

/**
 * Goods list search + pagination request. All filters optional.
 */
public record GoodsSearchRequest(
        Integer pageNumber,
        Integer pageSize,
        String keyword,
        Long categoryId,
        GoodsStatus status,
        String soldOut,
        String sortBy,
        String sortDir) {

    public int pageSizeOrDefault() {
        return pageSize == null || pageSize <= 0 ? 10 : pageSize;
    }

    public int offset() {
        int page = pageNumber == null || pageNumber < 0 ? 0 : pageNumber;
        return page * pageSizeOrDefault();
    }
}
