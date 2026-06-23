package org.streamhub.api.v1.playlist;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.base.storage.StorageService;
import org.streamhub.api.v1.album.entity.Album;
import org.streamhub.api.v1.album.entity.Track;
import org.streamhub.api.v1.album.repository.AlbumRepository;
import org.streamhub.api.v1.album.repository.TrackRepository;
import org.streamhub.api.v1.playlist.dto.PlaylistDetail;
import org.streamhub.api.v1.playlist.dto.PlaylistDto;
import org.streamhub.api.v1.playlist.dto.PlaylistSaveRequest;
import org.streamhub.api.v1.playlist.dto.PlaylistTrackItem;
import org.streamhub.api.v1.playlist.entity.Playlist;
import org.streamhub.api.v1.playlist.entity.PlaylistTrack;
import org.streamhub.api.v1.playlist.repository.PlaylistRepository;
import org.streamhub.api.v1.playlist.repository.PlaylistTrackRepository;

/**
 * Curated playlists: admin assembles ordered track collections; the music tab plays them for free.
 * A playlist references existing album tracks (it owns ordering, not the audio), so the user site
 * streams each track through the same album preview/full HLS endpoints.
 */
@Service
public class PlaylistService {

    private final PlaylistRepository playlistRepository;
    private final PlaylistTrackRepository playlistTrackRepository;
    private final TrackRepository trackRepository;
    private final AlbumRepository albumRepository;
    private final StorageService storageService;

    public PlaylistService(PlaylistRepository playlistRepository,
                           PlaylistTrackRepository playlistTrackRepository,
                           TrackRepository trackRepository,
                           AlbumRepository albumRepository,
                           StorageService storageService) {
        this.playlistRepository = playlistRepository;
        this.playlistTrackRepository = playlistTrackRepository;
        this.trackRepository = trackRepository;
        this.albumRepository = albumRepository;
        this.storageService = storageService;
    }

    // --- admin ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PlaylistDto> listAdmin() {
        return playlistRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public PlaylistDetail getAdminDetail(Long id) {
        Playlist playlist = playlistRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        return toDetail(playlist);
    }

    @Transactional
    public PlaylistDetail create(PlaylistSaveRequest request) {
        Playlist playlist = playlistRepository.save(Playlist.builder()
                .title(request.title())
                .description(request.description())
                .coverKey(request.coverKey())
                .sortOrder(request.sortOrder())
                .useYn(request.useYn())
                .build());
        replaceTracks(playlist.getId(), request.trackIds());
        return toDetail(playlist);
    }

    @Transactional
    public PlaylistDetail update(Long id, PlaylistSaveRequest request) {
        Playlist playlist = playlistRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        playlist.update(request.title(), request.description(), request.coverKey(),
                request.sortOrder(), request.useYn());
        playlistRepository.save(playlist);
        replaceTracks(id, request.trackIds());
        return toDetail(playlist);
    }

    @Transactional
    public void delete(Long id) {
        Playlist playlist = playlistRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        playlistTrackRepository.deleteByPlaylistId(id);
        playlistRepository.delete(playlist);
    }

    // --- public --------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PlaylistDto> listPublic() {
        return playlistRepository.findByUseYnOrderBySortOrderAscIdAsc("Y").stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public PlaylistDetail getPublicDetail(Long id) {
        Playlist playlist = playlistRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND));
        if (!"Y".equals(playlist.getUseYn())) {
            throw new ApiException(ResultCode.NOT_FOUND);
        }
        return toDetail(playlist);
    }

    // --- helpers -------------------------------------------------------------

    /** Replaces a playlist's tracks with {@code trackIds} (order = list order), ignoring missing ids. */
    private void replaceTracks(Long playlistId, List<Long> trackIds) {
        playlistTrackRepository.deleteByPlaylistId(playlistId);
        if (trackIds == null || trackIds.isEmpty()) {
            return;
        }
        Set<Long> existing = trackRepository.findAllById(trackIds).stream()
                .map(Track::getId)
                .collect(Collectors.toSet());
        Set<Long> ordered = new LinkedHashSet<>(trackIds); // de-dupe, keep order
        int order = 0;
        for (Long trackId : ordered) {
            if (!existing.contains(trackId)) {
                continue;
            }
            playlistTrackRepository.save(PlaylistTrack.builder()
                    .playlistId(playlistId).trackId(trackId).sortOrder(order++).build());
        }
    }

    private PlaylistDto toDto(Playlist playlist) {
        return PlaylistDto.builder()
                .id(playlist.getId())
                .title(playlist.getTitle())
                .description(playlist.getDescription())
                .coverKey(playlist.getCoverKey())
                .coverUrl(storageService.publicUrl(playlist.getCoverKey()))
                .sortOrder(playlist.getSortOrder())
                .useYn(playlist.getUseYn())
                .trackCount(playlistTrackRepository.countByPlaylistId(playlist.getId()))
                .build();
    }

    private PlaylistDetail toDetail(Playlist playlist) {
        return PlaylistDetail.builder()
                .id(playlist.getId())
                .title(playlist.getTitle())
                .description(playlist.getDescription())
                .coverKey(playlist.getCoverKey())
                .coverUrl(storageService.publicUrl(playlist.getCoverKey()))
                .sortOrder(playlist.getSortOrder())
                .useYn(playlist.getUseYn())
                .tracks(loadTracks(playlist.getId()))
                .build();
    }

    /** Resolves a playlist's ordered tracks, joining each to its album for artist/cover. */
    private List<PlaylistTrackItem> loadTracks(Long playlistId) {
        List<PlaylistTrack> entries = playlistTrackRepository.findByPlaylistIdOrderBySortOrderAsc(playlistId);
        if (entries.isEmpty()) {
            return List.of();
        }
        List<Long> trackIds = entries.stream().map(PlaylistTrack::getTrackId).toList();
        Map<Long, Track> tracks = trackRepository.findAllById(trackIds).stream()
                .collect(Collectors.toMap(Track::getId, Function.identity()));
        List<Long> albumIds = tracks.values().stream().map(Track::getAlbumId).distinct().toList();
        Map<Long, Album> albums = albumRepository.findAllById(albumIds).stream()
                .collect(Collectors.toMap(Album::getId, Function.identity()));

        List<PlaylistTrackItem> items = new ArrayList<>();
        for (PlaylistTrack entry : entries) {
            Track track = tracks.get(entry.getTrackId());
            if (track == null) {
                continue; // track deleted since it was added
            }
            Album album = albums.get(track.getAlbumId());
            items.add(PlaylistTrackItem.builder()
                    .id(track.getId())
                    .albumId(track.getAlbumId())
                    .trackNo(track.getTrackNo())
                    .title(track.getTitle())
                    .artist(album == null ? null : album.getArtist())
                    .albumTitle(album == null ? null : album.getTitle())
                    .coverUrl(album == null ? null : storageService.publicUrl(album.getCoverKey()))
                    .durationSec(track.getDurationSec())
                    .hasFullTrack(track.isHasFullTrack())
                    .hasPreviewHls(track.getPreviewHlsPrefix() != null)
                    .previewUrl(track.getPreviewUrl())
                    .previewStartSec(track.getPreviewStartSec())
                    .previewLengthSec(track.getPreviewLengthSec())
                    .build());
        }
        return items;
    }
}
