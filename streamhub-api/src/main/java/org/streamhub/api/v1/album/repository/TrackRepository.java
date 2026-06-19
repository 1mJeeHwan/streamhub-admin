package org.streamhub.api.v1.album.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.streamhub.api.v1.album.entity.Track;

/** JPA repository for {@link Track} (album tracks). */
public interface TrackRepository extends JpaRepository<Track, Long> {

    List<Track> findByAlbumIdOrderByTrackNoAsc(Long albumId);

    Optional<Track> findByIdAndAlbumId(Long id, Long albumId);

    void deleteByAlbumId(Long albumId);

    /**
     * Ids of tracks eligible for bulk encryption: not yet packaged ({@code hasFullTrack=false}) and
     * carrying a non-blank {@code previewUrl} to download and package. Returns ids (not entities) so
     * the async batch can re-load and re-check each track in its own transaction.
     */
    @Query("select t.id from Track t "
            + "where t.hasFullTrack = false and t.previewUrl is not null and trim(t.previewUrl) <> '' "
            + "order by t.id asc")
    List<Long> findUnpackagedTrackIdsWithPreview();

    /** Count of tracks already packaged as encrypted full tracks ({@code hasFullTrack=true}). */
    long countByHasFullTrackTrue();

    /** Count of tracks still eligible for bulk encryption (unpackaged with a non-blank previewUrl). */
    @Query("select count(t) from Track t "
            + "where t.hasFullTrack = false and t.previewUrl is not null and trim(t.previewUrl) <> ''")
    long countUnpackagedWithPreview();
}
