package org.streamhub.api.v1.goods;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResInfinityList;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.base.storage.StorageService;
import org.streamhub.api.base.util.SortResolver;
import org.streamhub.api.v1.actionlog.ActionLogPublisher;
import org.streamhub.api.v1.goods.dto.GoodsBulkUpdateRequest;
import org.streamhub.api.v1.goods.dto.GoodsCreateRequest;
import org.streamhub.api.v1.goods.dto.GoodsDetail;
import org.streamhub.api.v1.goods.dto.GoodsImageDto;
import org.streamhub.api.v1.goods.dto.GoodsListItem;
import org.streamhub.api.v1.goods.dto.GoodsOptionDto;
import org.streamhub.api.v1.goods.dto.GoodsSearchRequest;
import org.streamhub.api.v1.goods.entity.GoodsImage;
import org.streamhub.api.v1.goods.entity.GoodsItem;
import org.streamhub.api.v1.goods.entity.GoodsOption;
import org.streamhub.api.v1.goods.entity.GoodsStatus;
import org.streamhub.api.v1.goods.mapper.GoodsMapper;
import org.streamhub.api.v1.goods.repository.GoodsImageRepository;
import org.streamhub.api.v1.goods.repository.GoodsItemRepository;
import org.streamhub.api.v1.goods.repository.GoodsOptionRepository;

/**
 * Goods management: paginated search (MyBatis), CRUD (JPA), option/image replace-on-save,
 * inline bulk grid edits, and thumbnail/image URL resolution via {@link StorageService}.
 */
@Service
public class GoodsService {

    /** Whitelisted sort keys (GoodsListItem field → SQL column) for server-side list sorting. */
    private static final Map<String, String> GOODS_SORT_COLUMNS = Map.ofEntries(
            Map.entry("code", "gi.code"),
            Map.entry("name", "gi.name"),
            Map.entry("categoryName", "gc.name"),
            Map.entry("price", "gi.price"),
            Map.entry("listPrice", "gi.list_price"),
            Map.entry("stock", "gi.stock"),
            Map.entry("notiQty", "gi.noti_qty"),
            Map.entry("soldOut", "gi.sold_out"),
            Map.entry("useYn", "gi.use_yn"),
            Map.entry("status", "gi.status"),
            Map.entry("saleCount", "gi.sale_count"),
            Map.entry("viewCount", "gi.view_count"));

    private final GoodsMapper goodsMapper;
    private final GoodsItemRepository goodsItemRepository;
    private final GoodsOptionRepository goodsOptionRepository;
    private final GoodsImageRepository goodsImageRepository;
    private final StorageService storageService;
    private final ActionLogPublisher actionLogPublisher;

    public GoodsService(
            GoodsMapper goodsMapper,
            GoodsItemRepository goodsItemRepository,
            GoodsOptionRepository goodsOptionRepository,
            GoodsImageRepository goodsImageRepository,
            StorageService storageService,
            ActionLogPublisher actionLogPublisher) {
        this.goodsMapper = goodsMapper;
        this.goodsItemRepository = goodsItemRepository;
        this.goodsOptionRepository = goodsOptionRepository;
        this.goodsImageRepository = goodsImageRepository;
        this.storageService = storageService;
        this.actionLogPublisher = actionLogPublisher;
    }

    @Transactional(readOnly = true)
    public ResInfinityList<GoodsListItem> list(GoodsSearchRequest request) {
        String status = request.status() == null ? null : request.status().name();
        String keyword = blankToNull(request.keyword());
        String soldOut = blankToNull(request.soldOut());
        int size = request.pageSizeOrDefault();
        String orderBy = SortResolver.resolve(request.sortBy(), request.sortDir(),
                GOODS_SORT_COLUMNS, "gi.id", "gi.created_at DESC, gi.id DESC");

        List<GoodsListItem> items =
                goodsMapper.selectList(keyword, request.categoryId(), status, soldOut, orderBy, request.offset(), size);
        items.forEach(item -> item.setThumbnailUrl(storageService.publicUrl(item.getThumbnailKey())));
        long total = goodsMapper.countList(keyword, request.categoryId(), status, soldOut);
        return ResInfinityList.of(items, total, size);
    }

    @Transactional(readOnly = true)
    public GoodsDetail getDetail(Long id) {
        GoodsDetail detail = goodsMapper.selectDetail(id);
        if (detail == null) {
            throw new ApiException(ResultCode.NOT_FOUND);
        }
        detail.setThumbnailUrl(storageService.publicUrl(detail.getThumbnailKey()));
        detail.setOptions(loadOptions(id));
        detail.setImages(loadImages(id));
        return detail;
    }

    @Transactional
    public GoodsDetail create(GoodsCreateRequest request) {
        GoodsItem item = GoodsItem.builder()
                .categoryId(request.categoryId())
                .name(request.name())
                .code(request.code())
                .description(request.description())
                .price(request.price())
                .listPrice(request.listPrice())
                .stock(request.stock())
                .notiQty(request.notiQty())
                .soldOut(defaultYn(request.soldOut()))
                .useYn(defaultYn(request.useYn(), "Y"))
                .status(request.status() == null ? GoodsStatus.SELLING : request.status())
                .thumbnailKey(request.thumbnailKey())
                .badges(joinBadges(request.badges()))
                .build();
        GoodsItem saved = goodsItemRepository.save(item);
        replaceOptions(saved.getId(), request.options());
        replaceImages(saved.getId(), request.images());
        actionLogPublisher.publish("GOODS_CREATE", "GOODS", String.valueOf(saved.getId()), request.name());
        return getDetail(saved.getId());
    }

    @Transactional
    public GoodsDetail update(Long id, GoodsCreateRequest request) {
        GoodsItem item = goodsItemRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        item.update(
                request.categoryId(), request.name(), request.code(), request.description(),
                request.price(), request.listPrice(), request.stock(), request.notiQty(),
                defaultYn(request.soldOut()), defaultYn(request.useYn(), "Y"),
                request.status() == null ? item.getStatus() : request.status(),
                request.thumbnailKey(), joinBadges(request.badges()));
        goodsItemRepository.saveAndFlush(item);
        replaceOptions(id, request.options());
        replaceImages(id, request.images());
        actionLogPublisher.publish("GOODS_UPDATE", "GOODS", String.valueOf(id), request.name());
        return getDetail(id);
    }

    @Transactional
    public void delete(Long id) {
        GoodsItem item = goodsItemRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        goodsImageRepository.findByItemId(id).forEach(img -> storageService.delete(img.getS3Key()));
        goodsImageRepository.deleteByItemId(id);
        goodsOptionRepository.deleteByItemId(id);
        storageService.delete(item.getThumbnailKey());
        goodsItemRepository.delete(item);
        actionLogPublisher.publish("GOODS_DELETE", "GOODS", String.valueOf(id), item.getName());
    }

    /**
     * Applies inline grid edits to multiple items in one transaction. Unknown ids are
     * skipped; {@code null} cell values keep the existing value (JPA dirty checking flushes).
     *
     * @return number of rows actually updated
     */
    @Transactional
    public int bulkUpdate(GoodsBulkUpdateRequest request) {
        List<Long> ids = request.rows().stream().map(GoodsBulkUpdateRequest.Row::id).toList();
        Map<Long, GoodsItem> byId = new LinkedHashMap<>();
        goodsItemRepository.findAllByIdIn(ids).forEach(item -> byId.put(item.getId(), item));

        int affected = 0;
        for (GoodsBulkUpdateRequest.Row row : request.rows()) {
            GoodsItem item = byId.get(row.id());
            if (item == null) {
                continue;
            }
            item.applyInlineEdit(row.stock(), row.notiQty(), row.price(), row.soldOut(), row.useYn());
            affected++;
        }
        actionLogPublisher.publish("GOODS_BULK_UPDATE", "GOODS",
                ids.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse(""),
                affected + "건");
        return affected;
    }

    // --- helpers -----------------------------------------------------------

    /** Replaces an item's option rows (delete-then-reinsert). */
    private void replaceOptions(Long itemId, List<GoodsOptionDto> options) {
        goodsOptionRepository.deleteByItemId(itemId);
        if (options == null || options.isEmpty()) {
            return;
        }
        int order = 0;
        for (GoodsOptionDto dto : options) {
            if (!StringUtils.hasText(dto.getName())) {
                continue;
            }
            goodsOptionRepository.save(GoodsOption.builder()
                    .itemId(itemId)
                    .name(dto.getName())
                    .optionType(dto.getOptionType())
                    .extraPrice(dto.getExtraPrice())
                    .stock(dto.getStock())
                    .useYn(defaultYn(dto.getUseYn(), "Y"))
                    .sort(dto.getSort() != null ? dto.getSort() : order++)
                    .build());
        }
    }

    /** Replaces an item's gallery image rows (delete-then-reinsert). */
    private void replaceImages(Long itemId, List<GoodsImageDto> images) {
        goodsImageRepository.deleteByItemId(itemId);
        if (images == null || images.isEmpty()) {
            return;
        }
        int order = 0;
        for (GoodsImageDto dto : images) {
            if (!StringUtils.hasText(dto.getS3Key())) {
                continue;
            }
            goodsImageRepository.save(GoodsImage.builder()
                    .itemId(itemId)
                    .s3Key(dto.getS3Key())
                    .sort(dto.getSort() != null ? dto.getSort() : order++)
                    .build());
        }
    }

    private List<GoodsOptionDto> loadOptions(Long itemId) {
        return goodsOptionRepository.findByItemIdOrderBySortAscIdAsc(itemId).stream()
                .map(GoodsOptionDto::from)
                .toList();
    }

    private List<GoodsImageDto> loadImages(Long itemId) {
        return goodsImageRepository.findByItemIdOrderBySortAscIdAsc(itemId).stream()
                .map(img -> GoodsImageDto.of(img, storageService.publicUrl(img.getS3Key())))
                .toList();
    }

    private String joinBadges(List<String> badges) {
        if (badges == null || badges.isEmpty()) {
            return null;
        }
        String joined = badges.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .reduce((a, b) -> a + "," + b)
                .orElse(null);
        return StringUtils.hasText(joined) ? joined : null;
    }

    private String defaultYn(String value) {
        return defaultYn(value, "N");
    }

    private String defaultYn(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
