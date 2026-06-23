package org.streamhub.api.v1.playlist.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.streamhub.api.v1.playlist.entity.Playlist;

/** JPA repository for {@link Playlist}. */
public interface PlaylistRepository extends JpaRepository<Playlist, Long> {

    List<Playlist> findAllByOrderBySortOrderAscIdAsc();

    /** Visible playlists for the public music tab. */
    List<Playlist> findByUseYnOrderBySortOrderAscIdAsc(String useYn);
}
