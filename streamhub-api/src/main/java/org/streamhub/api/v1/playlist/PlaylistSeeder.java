package org.streamhub.api.v1.playlist;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.streamhub.api.v1.album.entity.Track;
import org.streamhub.api.v1.album.repository.TrackRepository;
import org.streamhub.api.v1.playlist.entity.Playlist;
import org.streamhub.api.v1.playlist.entity.PlaylistTrack;
import org.streamhub.api.v1.playlist.repository.PlaylistRepository;
import org.streamhub.api.v1.playlist.repository.PlaylistTrackRepository;

/**
 * Seeds a few curated demo playlists from existing album tracks. Idempotent (skips when playlists
 * exist) and wrapped in try/catch — a seeder must never crash production boot.
 */
@Slf4j
@Component
@Order(42)
public class PlaylistSeeder implements CommandLineRunner {

    private static final String COVER_Q = "?w=600&q=80&auto=format&fit=crop";

    /** {title, description, cover-photo-id} — license-free Unsplash covers (HTTP 200 verified). */
    private static final String[][] PLAYLISTS = {
            {"잔잔한 묵상 찬양", "마음을 가라앉히는 잔잔한 찬양 모음", "1465847899084-d164df4dedc6"},
            {"활기찬 워십 모음", "예배를 뜨겁게 여는 활기찬 워십", "1511671782779-c97d3d27a1d4"},
            {"새벽 기도 BGM", "새벽을 깨우는 묵상 음악", "1520523839897-bd0b52f945a0"},
    };
    private static final int TRACKS_PER_PLAYLIST = 6;

    private final PlaylistRepository playlistRepository;
    private final PlaylistTrackRepository playlistTrackRepository;
    private final TrackRepository trackRepository;

    public PlaylistSeeder(PlaylistRepository playlistRepository,
                          PlaylistTrackRepository playlistTrackRepository,
                          TrackRepository trackRepository) {
        this.playlistRepository = playlistRepository;
        this.playlistTrackRepository = playlistTrackRepository;
        this.trackRepository = trackRepository;
    }

    @Override
    public void run(String... args) {
        try {
            if (playlistRepository.count() > 0) {
                return;
            }
            List<Track> tracks = trackRepository.findAll();
            if (tracks.isEmpty()) {
                return;
            }
            int cursor = 0;
            int order = 0;
            for (String[] meta : PLAYLISTS) {
                Playlist playlist = playlistRepository.save(Playlist.builder()
                        .title(meta[0]).description(meta[1])
                        .coverKey("https://images.unsplash.com/photo-" + meta[2] + COVER_Q)
                        .sortOrder(order++).useYn("Y").build());
                int saved = 0;
                for (int i = 0; i < TRACKS_PER_PLAYLIST && cursor < tracks.size(); i++, cursor++) {
                    playlistTrackRepository.save(PlaylistTrack.builder()
                            .playlistId(playlist.getId())
                            .trackId(tracks.get(cursor).getId())
                            .sortOrder(saved++)
                            .build());
                }
                if (cursor >= tracks.size()) {
                    cursor = 0; // wrap so every playlist gets tracks even with a small catalog
                }
            }
            log.info("Seeded {} curated playlists", PLAYLISTS.length);
        } catch (RuntimeException e) {
            log.warn("Playlist seeding skipped: {}", e.getMessage());
        }
    }
}
