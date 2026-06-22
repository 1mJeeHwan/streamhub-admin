package org.streamhub.api.v1.album;

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
import org.streamhub.api.v1.album.dto.AlbumCreateRequest;
import org.streamhub.api.v1.album.dto.AlbumDetail;
import org.streamhub.api.v1.album.dto.AlbumListItem;
import org.streamhub.api.v1.album.dto.AlbumSearchRequest;
import org.streamhub.api.v1.album.dto.PreviewResponse;
import org.streamhub.api.v1.album.dto.TrackDto;
import org.streamhub.api.v1.album.entity.Album;
import org.streamhub.api.v1.album.entity.AlbumStatus;
import org.streamhub.api.v1.album.entity.MusicSource;
import org.streamhub.api.v1.album.entity.Track;
import org.streamhub.api.v1.album.mapper.AlbumMapper;
import org.streamhub.api.v1.album.provider.MusicPreviewProvider;
import org.streamhub.api.v1.album.repository.AlbumRepository;
import org.streamhub.api.v1.album.repository.TrackRepository;
import org.streamhub.api.v1.goods.entity.GoodsItem;
import org.streamhub.api.v1.goods.entity.GoodsStatus;
import org.streamhub.api.v1.goods.repository.GoodsCategoryRepository;
import org.streamhub.api.v1.goods.repository.GoodsItemRepository;

/**
 * Album management: paginated search (MyBatis), CRUD (JPA), track replace-on-save, and
 * the commerce bridge that mirrors each on-sale album to a {@code GOODS_ITEM} so the
 * order domain is never modified (C3 spec §3.4). Preview URLs are resolved through the
 * {@link MusicPreviewProvider} seam.
 */
@Service
public class AlbumService {

    private static final String GOODS_BRIDGE_CATEGORY = "음반";
    private static final int DEFAULT_NOTI_QTY = 5;

    /** Client sort key → safe column expression. Drives the public 인기/최신 album carousels. */
    private static final Map<String, String> ALBUM_SORT_COLUMNS = Map.of(
            "title", "a.title",
            "artist", "a.artist",
            "genre", "a.genre",
            "viewCount", "a.view_count",
            "releaseDate", "a.release_date",
            "createdAt", "a.created_at");

    private final AlbumMapper albumMapper;
    private final AlbumRepository albumRepository;
    private final TrackRepository trackRepository;
    private final GoodsItemRepository goodsItemRepository;
    private final GoodsCategoryRepository goodsCategoryRepository;
    private final StorageService storageService;
    private final ActionLogPublisher actionLogPublisher;
    private final MusicPreviewProvider musicPreviewProvider;

    public AlbumService(
            AlbumMapper albumMapper,
            AlbumRepository albumRepository,
            TrackRepository trackRepository,
            GoodsItemRepository goodsItemRepository,
            GoodsCategoryRepository goodsCategoryRepository,
            StorageService storageService,
            ActionLogPublisher actionLogPublisher,
            MusicPreviewProvider musicPreviewProvider) {
        this.albumMapper = albumMapper;
        this.albumRepository = albumRepository;
        this.trackRepository = trackRepository;
        this.goodsItemRepository = goodsItemRepository;
        this.goodsCategoryRepository = goodsCategoryRepository;
        this.storageService = storageService;
        this.actionLogPublisher = actionLogPublisher;
        this.musicPreviewProvider = musicPreviewProvider;
    }

    @Transactional(readOnly = true)
    public ResInfinityList<AlbumListItem> list(AlbumSearchRequest request) {
        String genre = request.genre() == null ? null : request.genre().name();
        String status = request.status() == null ? null : request.status().name();
        String keyword = blankToNull(request.keyword());
        int size = request.pageSizeOrDefault();
        String orderBy = SortResolver.resolve(request.sortBy(), request.sortDir(),
                ALBUM_SORT_COLUMNS, "a.id", "a.created_at DESC, a.id DESC");

        List<AlbumListItem> items =
                albumMapper.selectList(keyword, genre, status, orderBy, request.offset(), size);
        items.forEach(item -> item.setCoverUrl(storageService.publicUrl(item.getCoverKey())));
        long total = albumMapper.countList(keyword, genre, status);
        return ResInfinityList.of(items, total, size);
    }

    /** Public list: forces {@code status=ON_SALE}. */
    @Transactional(readOnly = true)
    public ResInfinityList<AlbumListItem> listPublic(AlbumSearchRequest request) {
        AlbumSearchRequest forced = new AlbumSearchRequest(
                request.pageNumber(), request.pageSize(), request.keyword(),
                request.genre(), AlbumStatus.ON_SALE,
                request.sortBy(), request.sortDir());
        return list(forced);
    }

    @Transactional(readOnly = true)
    public AlbumDetail getDetail(Long id) {
        AlbumDetail detail = albumMapper.selectDetail(id);
        if (detail == null) {
            throw new ApiException(ResultCode.NOT_FOUND);
        }
        detail.setCoverUrl(storageService.publicUrl(detail.getCoverKey()));
        detail.setTracks(loadTracks(id));
        return detail;
    }

    /** Public detail: 404 unless ON_SALE, and increments the view count. */
    @Transactional
    public AlbumDetail getPublicDetail(Long id) {
        Album album = albumRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        if (album.getStatus() != AlbumStatus.ON_SALE) {
            throw new ApiException(ResultCode.NOT_FOUND);
        }
        album.increaseViewCount();
        albumRepository.saveAndFlush(album);
        return getDetail(id);
    }

    @Transactional
    public AlbumDetail create(AlbumCreateRequest request) {
        Album album = Album.builder()
                .title(request.title())
                .artist(request.artist())
                .label(request.label())
                .genre(request.genre())
                .releaseDate(request.releaseDate())
                .description(request.description())
                .coverKey(request.coverKey())
                .status(request.status() == null ? AlbumStatus.ON_SALE : request.status())
                .source(MusicSource.SEED)
                .build();
        Album saved = albumRepository.save(album);
        syncGoodsBridge(saved, request.price(), request.stock());
        int trackCount = replaceTracks(saved.getId(), request.tracks());
        saved.syncTrackCount(trackCount);
        albumRepository.saveAndFlush(saved);
        actionLogPublisher.publish("ALBUM_CREATE", "ALBUM", String.valueOf(saved.getId()), request.title());
        return getDetail(saved.getId());
    }

    @Transactional
    public AlbumDetail update(Long id, AlbumCreateRequest request) {
        Album album = albumRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        album.update(
                request.title(), request.artist(), request.label(), request.genre(),
                request.releaseDate(), request.description(), request.coverKey(),
                request.status() == null ? album.getStatus() : request.status());
        syncGoodsBridge(album, request.price(), request.stock());
        int trackCount = replaceTracks(id, request.tracks());
        album.syncTrackCount(trackCount);
        albumRepository.saveAndFlush(album);
        actionLogPublisher.publish("ALBUM_UPDATE", "ALBUM", String.valueOf(id), request.title());
        return getDetail(id);
    }

    /**
     * Deletes an album and its tracks. The bridge {@code GOODS_ITEM} is <em>not</em>
     * physically deleted (past orders snapshot its id); it is parked as not-for-sale to
     * keep order history intact (C3 spec §8).
     */
    @Transactional
    public void delete(Long id) {
        Album album = albumRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        trackRepository.deleteByAlbumId(id);
        if (album.getGoodsItemId() != null) {
            goodsItemRepository.findById(album.getGoodsItemId()).ifPresent(gi ->
                    gi.applyInlineEdit(null, null, null, null, "N"));
        }
        storageService.delete(album.getCoverKey());
        albumRepository.delete(album);
        actionLogPublisher.publish("ALBUM_DELETE", "ALBUM", String.valueOf(id), album.getTitle());
    }

    /**
     * Public track preview: resolves the URL via the provider seam and tags the demo flag. A
     * provider that yields no playable URL (null/blank) is treated as "no preview available"
     * ({@code NOT_FOUND}) rather than shipping a broken URL to the mini-player.
     */
    @Transactional(readOnly = true)
    public PreviewResponse getPreview(Long albumId, Long trackId) {
        Track track = trackRepository.findByIdAndAlbumId(trackId, albumId)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        String url = musicPreviewProvider.resolvePreviewUrl(albumId, track.getTrackNo(), track.getPreviewUrl());
        if (!StringUtils.hasText(url)) {
            throw new ApiException(ResultCode.NOT_FOUND);
        }
        return new PreviewResponse(
                url, track.getPreviewStartSec(), track.getPreviewLengthSec(), musicPreviewProvider.isDemo());
    }

    // --- helpers -----------------------------------------------------------

    /**
     * Mirrors the album to its bridge {@code GOODS_ITEM} (create on first sale, sync on
     * edit). Order/stock/shipping/receipt logic stays entirely in the order+goods domain.
     */
    private void syncGoodsBridge(Album album, Long price, Integer stock) {
        long bridgePrice = price != null ? price : 0L;
        int bridgeStock = stock != null ? stock : 0;
        if (album.getGoodsItemId() == null) {
            GoodsItem item = goodsItemRepository.save(GoodsItem.builder()
                    .categoryId(bridgeCategoryId())
                    .name(album.getTitle())
                    .code("ALB" + album.getId())
                    .price(bridgePrice)
                    .stock(bridgeStock)
                    .notiQty(DEFAULT_NOTI_QTY)
                    .soldOut("N")
                    .useYn("Y")
                    .status(GoodsStatus.SELLING)
                    .thumbnailKey(album.getCoverKey())
                    .build());
            album.linkGoodsItem(item.getId());
        } else {
            GoodsItem item = goodsItemRepository.findById(album.getGoodsItemId())
                    .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
            item.update(
                    item.getCategoryId(), album.getTitle(), item.getCode(), item.getDescription(),
                    bridgePrice, item.getListPrice(), bridgeStock, item.getNotiQty(),
                    item.getSoldOut(), "Y", GoodsStatus.SELLING,
                    album.getCoverKey(), item.getBadges());
            goodsItemRepository.save(item);
        }
    }

    /** Replaces an album's tracks (delete-then-reinsert), returning the new track count. */
    private int replaceTracks(Long albumId, List<TrackDto> tracks) {
        trackRepository.deleteByAlbumId(albumId);
        if (tracks == null || tracks.isEmpty()) {
            return 0;
        }
        int no = 1;
        for (TrackDto dto : tracks) {
            if (!StringUtils.hasText(dto.getTitle())) {
                continue;
            }
            String previewUrl = musicPreviewProvider.resolvePreviewUrl(albumId, no, dto.getPreviewUrl());
            trackRepository.save(Track.builder()
                    .albumId(albumId)
                    .trackNo(no)
                    .title(dto.getTitle())
                    .durationSec(dto.getDurationSec())
                    .previewUrl(previewUrl)
                    .previewStartSec(dto.getPreviewStartSec())
                    .previewLengthSec(dto.getPreviewLengthSec())
                    .source(MusicSource.SEED)
                    .build());
            no++;
        }
        return no - 1;
    }

    /** Resolves track DTOs, re-resolving each preview URL through the provider seam. */
    private List<TrackDto> loadTracks(Long albumId) {
        return trackRepository.findByAlbumIdOrderByTrackNoAsc(albumId).stream()
                .map(track -> {
                    TrackDto dto = TrackDto.from(track);
                    dto.setPreviewUrl(musicPreviewProvider.resolvePreviewUrl(
                            albumId, track.getTrackNo(), track.getPreviewUrl()));
                    return dto;
                })
                .toList();
    }

    private Long bridgeCategoryId() {
        return goodsCategoryRepository.findAllByOrderBySortAscIdAsc().stream()
                .filter(c -> GOODS_BRIDGE_CATEGORY.equals(c.getName()))
                .map(org.streamhub.api.v1.goods.entity.GoodsCategory::getId)
                .findFirst()
                .orElseThrow(() -> new ApiException(
                        ResultCode.INTERNAL_ERROR, "음반 카테고리가 없습니다(시드 필요)"));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
