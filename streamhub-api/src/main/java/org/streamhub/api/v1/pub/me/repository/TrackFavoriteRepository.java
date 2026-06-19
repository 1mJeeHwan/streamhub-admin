package org.streamhub.api.v1.pub.me.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.streamhub.api.v1.pub.me.dto.FavoriteRow;
import org.streamhub.api.v1.pub.me.entity.TrackFavorite;

/** JPA repository for {@link TrackFavorite} (member playlist favorites). */
public interface TrackFavoriteRepository extends JpaRepository<TrackFavorite, Long> {

    boolean existsByMemberIdAndTrackId(Long memberId, Long trackId);

    void deleteByMemberIdAndTrackId(Long memberId, Long trackId);

    /**
     * A member's favorites joined to track + album, most recent first — the "재생목록 찜" feed.
     * {@code hasFullTrack} flags whether the encrypted full track is purchasable/playable.
     */
    @Query("""
            SELECT new org.streamhub.api.v1.pub.me.dto.FavoriteRow(
                       f.trackId, f.albumId, t.title, a.title, a.artist, a.coverKey, t.hasFullTrack)
            FROM TrackFavorite f, Track t, Album a
            WHERE t.id = f.trackId
              AND a.id = f.albumId
              AND f.memberId = :memberId
            ORDER BY f.id DESC
            """)
    List<FavoriteRow> findFavorites(@Param("memberId") Long memberId);
}
