package org.streamhub.api.v1.album.hls;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.v1.album.entity.Track;
import org.streamhub.api.v1.album.repository.TrackRepository;
import org.streamhub.api.v1.content.entity.Content;
import org.streamhub.api.v1.content.entity.ContentType;
import org.streamhub.api.v1.content.repository.ContentRepository;

/**
 * Orchestrates packaging of free music into <b>public</b> HLS: downloads the source audio
 * server-side (behind {@link SsrfGuard} via {@link HlsSampleDownloader}), transcodes it to plain
 * HLS ({@link HlsPackagingService#packagePublic}), and records the resulting S3 prefix on the owning
 * entity. Covers album previews ({@link Track#getPreviewUrl()}, capped to the preview window) and
 * audio content ({@link Content#getMediaUrl()}, full length).
 */
@Service
public class PublicHlsPackagingService {

    private static final int DEFAULT_PREVIEW_SECONDS = 30;

    private final TrackRepository trackRepository;
    private final ContentRepository contentRepository;
    private final HlsPackagingService packagingService;
    private final HlsSampleDownloader sampleDownloader;

    public PublicHlsPackagingService(TrackRepository trackRepository,
                                     ContentRepository contentRepository,
                                     HlsPackagingService packagingService,
                                     HlsSampleDownloader sampleDownloader) {
        this.trackRepository = trackRepository;
        this.contentRepository = contentRepository;
        this.packagingService = packagingService;
        this.sampleDownloader = sampleDownloader;
    }

    /** Packages a track's preview source into a public {@value #DEFAULT_PREVIEW_SECONDS}s HLS clip. */
    @Transactional
    public String packagePreview(Long trackId) {
        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND, "트랙을 찾을 수 없습니다"));
        String sourceUrl = track.getPreviewUrl();
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "프리뷰 소스 URL이 없는 트랙입니다");
        }
        int clip = track.getPreviewLengthSec() != null ? track.getPreviewLengthSec() : DEFAULT_PREVIEW_SECONDS;
        byte[] audio = sampleDownloader.download(sourceUrl);
        String prefix = "hls/preview/track-" + trackId + "/";
        packagingService.packagePublic(audio, prefix, "preview.mp3", clip);
        track.attachPreviewHls(prefix);
        trackRepository.save(track);
        return prefix;
    }

    /** Packages an audio (SOUND) content's source into a full-length public HLS stream. */
    @Transactional
    public String packageContent(Long contentId) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND, "콘텐츠를 찾을 수 없습니다"));
        if (content.getType() != ContentType.SOUND) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "음원(SOUND) 콘텐츠만 패키징할 수 있습니다");
        }
        String sourceUrl = content.getMediaUrl();
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "미디어 URL이 없는 콘텐츠입니다");
        }
        byte[] audio = sampleDownloader.download(sourceUrl);
        String prefix = "hls/content/" + contentId + "/";
        packagingService.packagePublic(audio, prefix, "content.mp3", 0);
        content.attachHls(prefix);
        contentRepository.save(content);
        return prefix;
    }

    /** Track ids that have a preview source but no packaged preview HLS yet. */
    @Transactional(readOnly = true)
    public List<Long> previewCandidates() {
        return trackRepository.findAll().stream()
                .filter(t -> t.getPreviewHlsPrefix() == null)
                .filter(t -> t.getPreviewUrl() != null && !t.getPreviewUrl().isBlank())
                .map(Track::getId)
                .toList();
    }

    /** Audio-content ids that have a media source but no packaged HLS yet. */
    @Transactional(readOnly = true)
    public List<Long> contentAudioCandidates() {
        return contentRepository.findAll().stream()
                .filter(c -> c.getType() == ContentType.SOUND)
                .filter(c -> c.getHlsPrefix() == null)
                .filter(c -> c.getMediaUrl() != null && !c.getMediaUrl().isBlank())
                .map(Content::getId)
                .toList();
    }
}
