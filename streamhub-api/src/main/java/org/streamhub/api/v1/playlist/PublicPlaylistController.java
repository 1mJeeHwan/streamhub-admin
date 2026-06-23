package org.streamhub.api.v1.playlist;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.streamhub.api.base.response.ResultDTO;
import org.streamhub.api.v1.playlist.dto.PlaylistDetail;
import org.streamhub.api.v1.playlist.dto.PlaylistDto;

/**
 * Public curated playlists for the music tab (visible playlists only). Tracks stream for free
 * through the album preview/full HLS endpoints.
 */
@Tag(name = "Public", description = "사용자 사이트용 공개 플레이리스트")
@RestController
@RequestMapping("/pub/v1/playlists")
public class PublicPlaylistController {

    private final PlaylistService playlistService;

    public PublicPlaylistController(PlaylistService playlistService) {
        this.playlistService = playlistService;
    }

    @Operation(summary = "공개 플레이리스트 목록", description = "노출(use_yn=Y) 플레이리스트를 정렬순으로 반환.")
    @GetMapping
    public ResultDTO<List<PlaylistDto>> list() {
        return ResultDTO.ok(playlistService.listPublic());
    }

    @Operation(summary = "공개 플레이리스트 상세", description = "수록 트랙 포함(전곡 무료 재생).")
    @GetMapping("/{id}")
    public ResultDTO<PlaylistDetail> detail(@PathVariable Long id) {
        return ResultDTO.ok(playlistService.getPublicDetail(id));
    }
}
