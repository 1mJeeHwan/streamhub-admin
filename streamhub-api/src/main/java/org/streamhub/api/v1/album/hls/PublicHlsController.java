package org.streamhub.api.v1.album.hls;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public (unauthenticated) playlists for free music — album previews and audio content. No key, no
 * purchase check (the streams are unencrypted), so the playlists are cacheable. Lives under
 * {@code /pub/**} (permitAll). Encrypted full tracks are served by {@link AlbumHlsPublicController}.
 */
@Tag(name = "Public HLS", description = "공개 음악 스트리밍 (프리뷰·음원)")
@RestController
public class PublicHlsController {

    private static final String HLS_MIME = "application/vnd.apple.mpegurl";

    private final PublicHlsStreamingService streamingService;

    public PublicHlsController(PublicHlsStreamingService streamingService) {
        this.streamingService = streamingService;
    }

    @Operation(summary = "프리뷰 스트림(공개 HLS)", description = "트랙의 30초 미리듣기 플레이리스트. 비암호·무인증.")
    @GetMapping(value = "/pub/v1/albums/{albumId}/tracks/{trackId}/preview/index.m3u8", produces = HLS_MIME)
    public ResponseEntity<String> previewPlaylist(@PathVariable Long albumId, @PathVariable Long trackId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofMinutes(5)).cachePublic())
                .contentType(MediaType.parseMediaType(HLS_MIME))
                .body(streamingService.previewPlaylist(albumId, trackId));
    }

    @Operation(summary = "음원 콘텐츠 스트림(공개 HLS)", description = "음원 콘텐츠의 플레이리스트. 비암호·무인증(PUBLISHED만).")
    @GetMapping(value = "/pub/v1/contents/{contentId}/hls/index.m3u8", produces = HLS_MIME)
    public ResponseEntity<String> contentPlaylist(@PathVariable Long contentId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofMinutes(5)).cachePublic())
                .contentType(MediaType.parseMediaType(HLS_MIME))
                .body(streamingService.contentPlaylist(contentId));
    }
}
