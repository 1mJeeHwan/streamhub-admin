package org.streamhub.api.v1.playlist.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.streamhub.api.v1.playlist.entity.PlaylistTrack;

/** JPA repository for {@link PlaylistTrack} (a playlist's ordered track entries). */
public interface PlaylistTrackRepository extends JpaRepository<PlaylistTrack, Long> {

    List<PlaylistTrack> findByPlaylistIdOrderBySortOrderAsc(Long playlistId);

    long countByPlaylistId(Long playlistId);

    void deleteByPlaylistId(Long playlistId);
}
