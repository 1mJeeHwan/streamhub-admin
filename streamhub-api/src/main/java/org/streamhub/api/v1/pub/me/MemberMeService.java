package org.streamhub.api.v1.pub.me;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResInfinityList;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.base.storage.StorageService;
import org.streamhub.api.v1.album.entity.Track;
import org.streamhub.api.v1.album.repository.AlbumRepository;
import org.streamhub.api.v1.album.repository.TrackRepository;
import org.streamhub.api.v1.content.entity.Content;
import org.streamhub.api.v1.content.repository.ContentRepository;
import org.streamhub.api.v1.goods.entity.GoodsItem;
import org.streamhub.api.v1.goods.inquiry.entity.AnswerStatus;
import org.streamhub.api.v1.goods.inquiry.entity.GoodsInquiry;
import org.streamhub.api.v1.goods.inquiry.repository.GoodsInquiryRepository;
import org.streamhub.api.v1.goods.repository.GoodsItemRepository;
import org.streamhub.api.v1.goods.review.entity.GoodsReview;
import org.streamhub.api.v1.goods.review.repository.GoodsReviewRepository;
import org.streamhub.api.v1.member.entity.Member;
import org.streamhub.api.v1.member.repository.MemberRepository;
import org.streamhub.api.v1.pub.me.dto.FavoriteItem;
import org.streamhub.api.v1.pub.me.dto.FavoriteRow;
import org.streamhub.api.v1.pub.me.dto.MyInquiryItem;
import org.streamhub.api.v1.pub.me.dto.MyReviewItem;
import org.streamhub.api.v1.pub.me.dto.PurchasedAlbumItem;
import org.streamhub.api.v1.pub.me.dto.PurchasedAlbumRow;
import org.streamhub.api.v1.pub.me.dto.WatchHistoryItem;
import org.streamhub.api.v1.pub.me.entity.TrackFavorite;
import org.streamhub.api.v1.pub.me.repository.TrackFavoriteRepository;
import org.streamhub.api.v1.statistics.entity.WatchHistory;
import org.streamhub.api.v1.statistics.repository.WatchHistoryRepository;

/**
 * Member "내 정보" (mypage) reads + writes under the public namespace: watch history, playlist
 * favorites, purchased albums, and the member's own reviews/inquiries. The member id is resolved by
 * the controller from the Bearer member token (never the admin SecurityContext), so every method
 * here is already scoped to one authenticated member.
 *
 * <p>This service is what finally <em>writes</em> {@code WATCH_HISTORY}: until now the table was a
 * dashboard-only read model with no producer. The user site's player now feeds it via
 * {@link #recordHistory}.
 */
@Slf4j
@Service
public class MemberMeService {

    private final WatchHistoryRepository watchHistoryRepository;
    private final ContentRepository contentRepository;
    private final TrackFavoriteRepository trackFavoriteRepository;
    private final TrackRepository trackRepository;
    private final AlbumRepository albumRepository;
    private final GoodsReviewRepository goodsReviewRepository;
    private final GoodsInquiryRepository goodsInquiryRepository;
    private final GoodsItemRepository goodsItemRepository;
    private final MemberRepository memberRepository;
    private final StorageService storageService;

    public MemberMeService(
            WatchHistoryRepository watchHistoryRepository,
            ContentRepository contentRepository,
            TrackFavoriteRepository trackFavoriteRepository,
            TrackRepository trackRepository,
            AlbumRepository albumRepository,
            GoodsReviewRepository goodsReviewRepository,
            GoodsInquiryRepository goodsInquiryRepository,
            GoodsItemRepository goodsItemRepository,
            MemberRepository memberRepository,
            StorageService storageService) {
        this.watchHistoryRepository = watchHistoryRepository;
        this.contentRepository = contentRepository;
        this.trackFavoriteRepository = trackFavoriteRepository;
        this.trackRepository = trackRepository;
        this.albumRepository = albumRepository;
        this.goodsReviewRepository = goodsReviewRepository;
        this.goodsInquiryRepository = goodsInquiryRepository;
        this.goodsItemRepository = goodsItemRepository;
        this.memberRepository = memberRepository;
        this.storageService = storageService;
    }

    // --- Watch history ---------------------------------------------------------------------------

    /**
     * Records one watch event (not idempotent — every player ping is a new row). Best-effort: a
     * failure to persist history must never break playback, so it is logged and swallowed.
     */
    @Transactional
    public void recordHistory(Long memberId, Long contentId, Integer watchSeconds) {
        try {
            watchHistoryRepository.save(WatchHistory.builder()
                    .memberId(memberId)
                    .contentId(contentId)
                    .watchedAt(LocalDateTime.now())
                    .watchSeconds(watchSeconds != null ? watchSeconds : 0)
                    .build());
        } catch (RuntimeException e) {
            log.warn("watch-history record failed (member={}, content={}): {}",
                    memberId, contentId, e.getMessage());
        }
    }

    /** The member's watch events most recent first, content metadata joined, one page at a time. */
    @Transactional(readOnly = true)
    public ResInfinityList<WatchHistoryItem> history(Long memberId, int pageNumber, int pageSize) {
        int size = clampPageSize(pageSize);
        Page<WatchHistory> page = watchHistoryRepository
                .findByMemberIdOrderByWatchedAtDesc(memberId, PageRequest.of(clampPageNumber(pageNumber), size));
        List<WatchHistory> events = page.getContent();
        Map<Long, Content> contents = contentRepository
                .findAllById(events.stream().map(WatchHistory::getContentId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Content::getId, Function.identity()));
        List<WatchHistoryItem> items = events.stream()
                .map(e -> toHistoryItem(e, contents.get(e.getContentId())))
                .toList();
        return ResInfinityList.of(items, page.getTotalElements(), size);
    }

    private static int clampPageNumber(int pageNumber) {
        return Math.max(pageNumber, 0);
    }

    private static int clampPageSize(int pageSize) {
        return Math.min(Math.max(pageSize, 1), 100);
    }

    private WatchHistoryItem toHistoryItem(WatchHistory event, Content content) {
        return new WatchHistoryItem(
                event.getContentId(),
                content != null ? content.getTitle() : null,
                content != null ? content.getType() : null,
                content != null ? storageService.publicUrl(content.getThumbnailKey()) : null,
                event.getWatchedAt(),
                event.getWatchSeconds());
    }

    // --- Playlist favorites ----------------------------------------------------------------------

    /**
     * Adds a track to the member's favorites. Idempotent: an existing favorite is a no-op. The
     * track's album id is denormalised onto the favorite row at add time.
     */
    @Transactional
    public void addFavorite(Long memberId, Long trackId) {
        if (trackFavoriteRepository.existsByMemberIdAndTrackId(memberId, trackId)) {
            return;
        }
        Long albumId = trackRepository.findById(trackId).map(Track::getAlbumId).orElse(null);
        if (albumId == null) {
            // Unknown track — nothing to favorite. Stay silent (best-effort, mirrors history).
            return;
        }
        trackFavoriteRepository.save(TrackFavorite.builder()
                .memberId(memberId)
                .trackId(trackId)
                .albumId(albumId)
                .build());
    }

    /** Removes a track from the member's favorites (no-op if absent). */
    @Transactional
    public void removeFavorite(Long memberId, Long trackId) {
        trackFavoriteRepository.deleteByMemberIdAndTrackId(memberId, trackId);
    }

    /** The member's favorites, track + album metadata joined, cover keys resolved to URLs. */
    @Transactional(readOnly = true)
    public List<FavoriteItem> favorites(Long memberId) {
        return trackFavoriteRepository.findFavorites(memberId).stream()
                .map(this::toFavoriteItem)
                .toList();
    }

    private FavoriteItem toFavoriteItem(FavoriteRow row) {
        return new FavoriteItem(
                row.trackId(),
                row.albumId(),
                row.trackTitle(),
                row.albumTitle(),
                row.artist(),
                storageService.publicUrl(row.coverKey()),
                row.hasFullTrack());
    }

    // --- Purchased albums ------------------------------------------------------------------------

    /** Albums the member has paid for (deduplicated), one page at a time, cover keys resolved to URLs. */
    @Transactional(readOnly = true)
    public ResInfinityList<PurchasedAlbumItem> purchasedAlbums(Long memberId, int pageNumber, int pageSize) {
        int size = clampPageSize(pageSize);
        Pageable pageable = PageRequest.of(clampPageNumber(pageNumber), size);
        Page<PurchasedAlbumRow> page = albumRepository.findPurchasedAlbums(memberId, pageable);
        List<PurchasedAlbumItem> contents = page.getContent().stream()
                .map(this::toPurchasedAlbumItem)
                .toList();
        return ResInfinityList.of(contents, page.getTotalElements(), size);
    }

    private PurchasedAlbumItem toPurchasedAlbumItem(PurchasedAlbumRow row) {
        return new PurchasedAlbumItem(
                row.albumId(),
                row.title(),
                row.artist(),
                storageService.publicUrl(row.coverKey()),
                row.purchasedAt());
    }

    // --- My reviews / inquiries ------------------------------------------------------------------

    /** The member's own goods reviews, goods name joined. */
    @Transactional(readOnly = true)
    public List<MyReviewItem> reviews(Long memberId) {
        List<GoodsReview> reviews = goodsReviewRepository.findByMemberIdOrderByIdDesc(memberId);
        Map<Long, String> names = goodsNames(reviews.stream().map(GoodsReview::getGoodsItemId).toList());
        return reviews.stream()
                .map(r -> toReviewItem(r, names.get(r.getGoodsItemId())))
                .toList();
    }

    /**
     * Writes a member review of a goods item: persists it (visible by default) and returns the
     * created row with the goods name joined.
     */
    @Transactional
    public MyReviewItem createReview(Long memberId, Long goodsItemId, int rating, String content) {
        String memberName = memberName(memberId);
        GoodsReview review = goodsReviewRepository.save(GoodsReview.builder()
                .goodsItemId(goodsItemId)
                .memberId(memberId)
                .memberName(memberName)
                .rating(rating)
                .content(content)
                .displayYn("Y")
                .createdAt(LocalDateTime.now())
                .build());
        return toReviewItem(review, goodsName(goodsItemId));
    }

    private MyReviewItem toReviewItem(GoodsReview review, String goodsName) {
        return new MyReviewItem(
                review.getId(),
                review.getGoodsItemId(),
                goodsName,
                review.getRating(),
                review.getContent(),
                review.getCreatedAt());
    }

    /** The member's own goods inquiries, goods name joined. */
    @Transactional(readOnly = true)
    public List<MyInquiryItem> inquiries(Long memberId) {
        List<GoodsInquiry> inquiries = goodsInquiryRepository.findByMemberIdOrderByIdDesc(memberId);
        Map<Long, String> names = goodsNames(inquiries.stream().map(GoodsInquiry::getGoodsItemId).toList());
        return inquiries.stream()
                .map(i -> toInquiryItem(i, names.get(i.getGoodsItemId())))
                .toList();
    }

    /**
     * Writes a member inquiry about a goods item: persists it WAITING for an answer and returns the
     * created row with the goods name joined.
     */
    @Transactional
    public MyInquiryItem createInquiry(Long memberId, Long goodsItemId, String title, String content) {
        String memberName = memberName(memberId);
        GoodsInquiry inquiry = goodsInquiryRepository.save(GoodsInquiry.builder()
                .goodsItemId(goodsItemId)
                .memberId(memberId)
                .memberName(memberName)
                .title(title)
                .content(content)
                .answerStatus(AnswerStatus.WAITING)
                .createdAt(LocalDateTime.now())
                .build());
        return toInquiryItem(inquiry, goodsName(goodsItemId));
    }

    private MyInquiryItem toInquiryItem(GoodsInquiry inquiry, String goodsName) {
        return new MyInquiryItem(
                inquiry.getId(),
                inquiry.getGoodsItemId(),
                goodsName,
                inquiry.getTitle(),
                inquiry.getContent(),
                inquiry.getAnswerContent(),
                inquiry.getAnswerStatus(),
                inquiry.getCreatedAt());
    }

    /** Resolves a single member's display name, 404 if the member is missing. */
    private String memberName(Long memberId) {
        return memberRepository.findById(memberId)
                .map(Member::getName)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
    }

    /** Resolves a single goods item's name ({@code null} if it was deleted). */
    private String goodsName(Long goodsItemId) {
        return goodsItemRepository.findById(goodsItemId).map(GoodsItem::getName).orElse(null);
    }

    /** Batch-resolves goods ids → names (one query), so review/inquiry rows avoid N+1 lookups. */
    private Map<Long, String> goodsNames(List<Long> goodsIds) {
        List<Long> distinct = goodsIds.stream().distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return goodsItemRepository.findAllByIdIn(distinct).stream()
                .collect(Collectors.toMap(GoodsItem::getId, GoodsItem::getName));
    }
}
