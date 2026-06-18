package org.streamhub.api.v1.goods.stock;

import java.util.Comparator;
import java.util.List;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.v1.actionlog.ActionLogPublisher;
import org.streamhub.api.v1.goods.entity.GoodsItem;
import org.streamhub.api.v1.goods.stock.dto.GoodsStockDto;
import org.streamhub.api.v1.goods.stock.dto.GoodsStockSearchRequest;
import org.streamhub.api.v1.goods.stock.dto.GoodsStockUpdateRequest;

/**
 * Stock (inventory) management for goods items: a filterable listing plus per-item stock
 * edits and a sold-out toggle. Reuses the existing seeded {@link GoodsItem} data.
 *
 * <p>{@link org.streamhub.api.v1.goods.entity.GoodsStatus} only models SELLING/PAUSED, so
 * the sold-out state is the item's own {@code soldOut} "Y"/"N" flag, which this toggle flips.
 */
@Service
public class GoodsStockService {

    private final GoodsStockRepository goodsStockRepository;
    private final ActionLogPublisher actionLogPublisher;

    public GoodsStockService(GoodsStockRepository goodsStockRepository,
                             ActionLogPublisher actionLogPublisher) {
        this.goodsStockRepository = goodsStockRepository;
        this.actionLogPublisher = actionLogPublisher;
    }

    /**
     * Stock listing. Filters by keyword (code/name) and low-stock; orders by stock
     * ascending when requested, otherwise newest first.
     */
    @Transactional(readOnly = true)
    public List<GoodsStockDto> list(GoodsStockSearchRequest request) {
        boolean lowStockOnly = request != null && "Y".equalsIgnoreCase(request.lowStock());
        boolean byStockAsc = request != null && Boolean.TRUE.equals(request.sortByStockAsc());
        String keyword = request != null && request.keyword() != null
                ? request.keyword().trim().toLowerCase() : null;

        Comparator<GoodsItem> order = byStockAsc
                ? Comparator.comparing(GoodsItem::getStock)
                : Comparator.comparing(GoodsItem::getId).reversed();

        return goodsStockRepository.findAll().stream()
                .filter(item -> keyword == null || keyword.isBlank()
                        || item.getName().toLowerCase().contains(keyword)
                        || item.getCode().toLowerCase().contains(keyword))
                .filter(item -> !lowStockOnly || item.getStock() <= item.getNotiQty())
                .sorted(order)
                .map(GoodsStockDto::from)
                .toList();
    }

    /**
     * Updates an item's stock (and optionally its low-stock notify threshold). This is an
     * absolute set (admin override), so it relies on {@link GoodsItem}'s {@code @Version}
     * optimistic lock: a concurrent edit fails fast rather than silently clobbering the other
     * admin's value.
     *
     * @throws ApiException {@code INVALID_PARAMETER} if a concurrent edit won the race
     */
    @Transactional
    public GoodsStockDto updateStock(Long id, GoodsStockUpdateRequest request) {
        if (request == null || request.getStock() == null) {
            throw new ApiException(ResultCode.INVALID_PARAMETER);
        }
        GoodsItem item = goodsStockRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        item.applyInlineEdit(request.getStock(), request.getNotiQty(), null, null, null);
        flushWithLockCheck(item);
        actionLogPublisher.publish(
                "GOODS_STOCK_UPDATE", "GOODS_ITEM", String.valueOf(id), item.getName());
        return GoodsStockDto.from(item);
    }

    /**
     * Toggles the item's sold-out flag ("Y" ↔ "N"). Guarded by the same {@code @Version}
     * optimistic lock as {@link #updateStock}.
     *
     * @throws ApiException {@code INVALID_PARAMETER} if a concurrent edit won the race
     */
    @Transactional
    public GoodsStockDto toggleSoldOut(Long id) {
        GoodsItem item = goodsStockRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        String next = "Y".equals(item.getSoldOut()) ? "N" : "Y";
        item.applyInlineEdit(null, null, null, next, null);
        flushWithLockCheck(item);
        actionLogPublisher.publish(
                "GOODS_SOLDOUT_TOGGLE", "GOODS_ITEM", String.valueOf(id), next);
        return GoodsStockDto.from(item);
    }

    /**
     * Persists a stock edit, translating an optimistic-lock failure into a clean
     * {@link ApiException} ({@code INVALID_PARAMETER}) instead of a raw 500 from the
     * global handler.
     */
    private void flushWithLockCheck(GoodsItem item) {
        try {
            goodsStockRepository.saveAndFlush(item);
        } catch (OptimisticLockingFailureException e) {
            throw new ApiException(ResultCode.INVALID_PARAMETER,
                    "다른 사용자가 먼저 재고를 수정했습니다. 새로고침 후 다시 시도해 주세요");
        }
    }
}
