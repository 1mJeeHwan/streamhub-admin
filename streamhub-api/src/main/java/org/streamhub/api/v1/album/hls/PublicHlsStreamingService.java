package org.streamhub.api.v1.album.hls;

import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.base.storage.StorageService;
import org.streamhub.api.v1.album.entity.Track;
import org.streamhub.api.v1.album.repository.TrackRepository;
import org.streamhub.api.v1.content.entity.Content;
import org.streamhub.api.v1.content.entity.ContentStatus;
import org.streamhub.api.v1.content.repository.ContentRepository;

/**
 * Serves <b>public, unencrypted</b> HLS playlists for free music — album previews and audio content.
 *
 * <p>Unlike {@link HlsStreamingService} (encrypted full tracks, key-gated), there is no AES key and
 * no purchase check: the playlist and segments are freely cacheable. The stored {@code index.m3u8}
 * is read from storage and rewritten so each segment points at the CDN ({@code segment-base-url});
 * there is no {@code #EXT-X-KEY} line to rewrite.
 */
@Service
public class PublicHlsStreamingService {

    private final StorageService storageService;
    private final TrackRepository trackRepository;
    private final ContentRepository contentRepository;
    private final String segmentBaseUrl;

    public PublicHlsStreamingService(StorageService storageService,
                                     TrackRepository trackRepository,
                                     ContentRepository contentRepository,
                                     @Value("${app.hls.segment-base-url:}") String segmentBaseUrl) {
        this.storageService = storageService;
        this.trackRepository = trackRepository;
        this.contentRepository = contentRepository;
        this.segmentBaseUrl = stripTrailingSlash(segmentBaseUrl);
    }

    /** Public: a track's 30-second preview playlist (segments → CDN). */
    @Transactional(readOnly = true)
    public String previewPlaylist(Long albumId, Long trackId) {
        Track track = trackRepository.findByIdAndAlbumId(trackId, albumId)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND, "트랙을 찾을 수 없습니다"));
        String prefix = track.getPreviewHlsPrefix();
        if (prefix == null) {
            throw new ApiException(ResultCode.NOT_FOUND, "프리뷰 스트림이 없습니다");
        }
        return readAndRewrite(prefix);
    }

    /** Public: an audio content's playlist (segments → CDN). PUBLISHED content only. */
    @Transactional(readOnly = true)
    public String contentPlaylist(Long contentId) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND, "콘텐츠를 찾을 수 없습니다"));
        if (content.getStatus() != ContentStatus.PUBLISHED) {
            throw new ApiException(ResultCode.NOT_FOUND);
        }
        String prefix = content.getHlsPrefix();
        if (prefix == null) {
            throw new ApiException(ResultCode.NOT_FOUND, "스트림이 없습니다");
        }
        return readAndRewrite(prefix);
    }

    // --- helpers -----------------------------------------------------------

    private String readAndRewrite(String prefix) {
        String stored = new String(storageService.getBytes(prefix + "index.m3u8"), StandardCharsets.UTF_8);
        return rewrite(stored, prefix);
    }

    /** Rewrites each relative segment file to an absolute CDN URL; other lines pass through. */
    private String rewrite(String m3u8, String prefix) {
        StringBuilder out = new StringBuilder(m3u8.length() + 256);
        for (String line : m3u8.split("\n", -1)) {
            String trimmed = line.strip();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#") && trimmed.endsWith(".ts")) {
                out.append(segmentBaseUrl).append('/').append(prefix).append(trimmed).append('\n');
            } else if (!trimmed.isEmpty() || line.isEmpty()) {
                out.append(line).append('\n');
            }
        }
        return out.toString();
    }

    private String stripTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
