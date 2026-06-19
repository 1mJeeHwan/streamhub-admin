package org.streamhub.api.v1.pub.me;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.streamhub.api.base.storage.StorageService;
import org.streamhub.api.v1.album.entity.Track;
import org.streamhub.api.v1.album.repository.AlbumRepository;
import org.streamhub.api.v1.album.repository.TrackRepository;
import org.streamhub.api.v1.content.entity.Content;
import org.streamhub.api.v1.content.entity.ContentType;
import org.streamhub.api.v1.content.repository.ContentRepository;
import org.streamhub.api.v1.goods.inquiry.repository.GoodsInquiryRepository;
import org.streamhub.api.v1.goods.repository.GoodsItemRepository;
import org.streamhub.api.v1.goods.review.repository.GoodsReviewRepository;
import org.streamhub.api.v1.pub.me.dto.WatchHistoryItem;
import org.streamhub.api.v1.pub.me.entity.TrackFavorite;
import org.streamhub.api.v1.pub.me.repository.TrackFavoriteRepository;
import org.streamhub.api.v1.statistics.entity.WatchHistory;
import org.streamhub.api.v1.statistics.repository.WatchHistoryRepository;

/**
 * Unit tests for the member mypage service logic:
 * <ul>
 *   <li>favorite-add is idempotent (an existing favorite saves nothing) and otherwise denormalises
 *       the track's album id onto the new row;</li>
 *   <li>history record is best-effort (a repository failure is swallowed, never propagated);</li>
 *   <li>history read joins content metadata and resolves the thumbnail key to a public URL.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class MemberMeServiceTest {

    @Mock private WatchHistoryRepository watchHistoryRepository;
    @Mock private ContentRepository contentRepository;
    @Mock private TrackFavoriteRepository trackFavoriteRepository;
    @Mock private TrackRepository trackRepository;
    @Mock private AlbumRepository albumRepository;
    @Mock private GoodsReviewRepository goodsReviewRepository;
    @Mock private GoodsInquiryRepository goodsInquiryRepository;
    @Mock private GoodsItemRepository goodsItemRepository;
    @Mock private StorageService storageService;

    private MemberMeService service() {
        return new MemberMeService(
                watchHistoryRepository, contentRepository, trackFavoriteRepository, trackRepository,
                albumRepository, goodsReviewRepository, goodsInquiryRepository, goodsItemRepository,
                storageService);
    }

    @Test
    void addFavorite_whenAlreadyFavorited_isNoOp() {
        when(trackFavoriteRepository.existsByMemberIdAndTrackId(1L, 5L)).thenReturn(true);

        service().addFavorite(1L, 5L);

        verify(trackRepository, never()).findById(any());
        verify(trackFavoriteRepository, never()).save(any());
    }

    @Test
    void addFavorite_whenNew_denormalisesAlbumIdFromTrack() {
        Track track = Track.builder().albumId(42L).trackNo(1).title("곡").build();
        ReflectionTestUtils.setField(track, "id", 5L);
        when(trackFavoriteRepository.existsByMemberIdAndTrackId(1L, 5L)).thenReturn(false);
        when(trackRepository.findById(5L)).thenReturn(Optional.of(track));

        service().addFavorite(1L, 5L);

        ArgumentCaptor<TrackFavorite> captor = ArgumentCaptor.forClass(TrackFavorite.class);
        verify(trackFavoriteRepository).save(captor.capture());
        TrackFavorite saved = captor.getValue();
        assertThat(saved.getMemberId()).isEqualTo(1L);
        assertThat(saved.getTrackId()).isEqualTo(5L);
        assertThat(saved.getAlbumId()).isEqualTo(42L);
    }

    @Test
    void addFavorite_whenTrackMissing_savesNothing() {
        when(trackFavoriteRepository.existsByMemberIdAndTrackId(1L, 5L)).thenReturn(false);
        when(trackRepository.findById(5L)).thenReturn(Optional.empty());

        service().addFavorite(1L, 5L);

        verify(trackFavoriteRepository, never()).save(any());
    }

    @Test
    void recordHistory_persistsEvent_withDefaultSecondsZero() {
        service().recordHistory(1L, 9L, null);

        ArgumentCaptor<WatchHistory> captor = ArgumentCaptor.forClass(WatchHistory.class);
        verify(watchHistoryRepository).save(captor.capture());
        WatchHistory saved = captor.getValue();
        assertThat(saved.getMemberId()).isEqualTo(1L);
        assertThat(saved.getContentId()).isEqualTo(9L);
        assertThat(saved.getWatchSeconds()).isZero();
    }

    @Test
    void recordHistory_swallowsRepositoryFailure() {
        when(watchHistoryRepository.save(any())).thenThrow(new RuntimeException("db down"));

        // Best-effort: playback must not break — no exception escapes.
        service().recordHistory(1L, 9L, 30);

        verify(watchHistoryRepository).save(any());
    }

    @Test
    void history_joinsContentMetadata_andResolvesThumbnailUrl() {
        WatchHistory event = WatchHistory.builder()
                .memberId(1L).contentId(9L).watchedAt(LocalDateTime.now()).watchSeconds(120).build();
        Content content = Content.builder()
                .channelId(1L).type(ContentType.VIDEO).title("예배 실황")
                .thumbnailKey("thumb/9.jpg").status(null).build();
        ReflectionTestUtils.setField(content, "id", 9L);

        when(watchHistoryRepository.findByMemberIdOrderByWatchedAtDesc(any(), any(Pageable.class)))
                .thenReturn(List.of(event));
        when(contentRepository.findAllById(any())).thenReturn(List.of(content));
        when(storageService.publicUrl("thumb/9.jpg")).thenReturn("https://cdn/thumb/9.jpg");

        List<WatchHistoryItem> items = service().history(1L);

        assertThat(items).hasSize(1);
        WatchHistoryItem item = items.get(0);
        assertThat(item.contentId()).isEqualTo(9L);
        assertThat(item.title()).isEqualTo("예배 실황");
        assertThat(item.type()).isEqualTo(ContentType.VIDEO);
        assertThat(item.thumbnailUrl()).isEqualTo("https://cdn/thumb/9.jpg");
        assertThat(item.watchSeconds()).isEqualTo(120);
    }
}
