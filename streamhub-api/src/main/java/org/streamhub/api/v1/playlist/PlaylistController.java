package org.streamhub.api.v1.playlist;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.streamhub.api.base.response.ResultDTO;
import org.streamhub.api.v1.playlist.dto.PlaylistDetail;
import org.streamhub.api.v1.playlist.dto.PlaylistDto;
import org.streamhub.api.v1.playlist.dto.PlaylistSaveRequest;

/**
 * Admin management of curated playlists. SYSTEM/CHURCH_MANAGER write, VIEWER read (via
 * {@code playlist:read}).
 */
@Tag(name = "Playlist", description = "큐레이션 플레이리스트 관리")
@RestController
@RequestMapping("/v1/playlist")
@PreAuthorize("hasAuthority('playlist:read')")
public class PlaylistController {

    private final PlaylistService playlistService;

    public PlaylistController(PlaylistService playlistService) {
        this.playlistService = playlistService;
    }

    @Operation(summary = "플레이리스트 목록")
    @GetMapping
    public ResultDTO<List<PlaylistDto>> list() {
        return ResultDTO.ok(playlistService.listAdmin());
    }

    @Operation(summary = "플레이리스트 상세", description = "수록 트랙 포함.")
    @GetMapping("/{id}")
    public ResultDTO<PlaylistDetail> detail(@PathVariable Long id) {
        return ResultDTO.ok(playlistService.getAdminDetail(id));
    }

    @Operation(summary = "플레이리스트 생성")
    @PreAuthorize("hasAuthority('playlist:write')")
    @PostMapping
    public ResultDTO<PlaylistDetail> create(@Valid @RequestBody PlaylistSaveRequest request) {
        return ResultDTO.ok(playlistService.create(request));
    }

    @Operation(summary = "플레이리스트 수정", description = "트랙 목록은 전체 교체(순서=목록 순서).")
    @PreAuthorize("hasAuthority('playlist:write')")
    @PutMapping("/{id}")
    public ResultDTO<PlaylistDetail> update(
            @PathVariable Long id, @Valid @RequestBody PlaylistSaveRequest request) {
        return ResultDTO.ok(playlistService.update(id, request));
    }

    @Operation(summary = "플레이리스트 삭제")
    @PreAuthorize("hasAuthority('playlist:write')")
    @DeleteMapping("/{id}")
    public ResultDTO<Void> delete(@PathVariable Long id) {
        playlistService.delete(id);
        return ResultDTO.ok();
    }
}
