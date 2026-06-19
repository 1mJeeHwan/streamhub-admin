package org.streamhub.api.v1.album.hls;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.base.response.ResultDTO;
import org.streamhub.api.v1.album.entity.Track;
import org.streamhub.api.v1.album.repository.TrackRepository;

/**
 * Admin endpoints to package a track's encrypted full-track HLS asset.
 *
 * <ul>
 *   <li>{@code POST .../audio} — upload a real full-track file (mp3/wav/...) → encrypted HLS.</li>
 *   <li>{@code POST .../audio/demo} — package the track's existing demo sample
 *       ({@code previewUrl}) so the encrypted-streaming pipeline can be demonstrated end-to-end
 *       without a real copyrighted master.</li>
 * </ul>
 *
 * <p>The demo sample is fetched server-side through {@link HlsSampleDownloader}, which validates the
 * URL with {@link SsrfGuard} (a {@code previewUrl} is admin-settable free text — without validation
 * it could be pointed at internal services or cloud metadata).
 */
@Tag(name = "Album HLS Admin", description = "암호화 풀트랙 패키징 (관리자)")
@RestController
@RequestMapping("/v1/album/tracks/{trackId}")
@PreAuthorize("hasAnyAuthority(T(org.streamhub.api.base.security.AuthoritiesConstants).SYSTEM, "
        + "T(org.streamhub.api.base.security.AuthoritiesConstants).CHURCH_MANAGER)")
public class AlbumHlsAdminController {

    private final HlsPackagingService packagingService;
    private final TrackRepository trackRepository;
    private final HlsSampleDownloader sampleDownloader;

    public AlbumHlsAdminController(HlsPackagingService packagingService,
                                   TrackRepository trackRepository,
                                   HlsSampleDownloader sampleDownloader) {
        this.packagingService = packagingService;
        this.trackRepository = trackRepository;
        this.sampleDownloader = sampleDownloader;
    }

    @Operation(summary = "풀트랙 업로드·암호화", description = "오디오 파일을 AES-128 암호화 HLS로 패키징해 저장한다.")
    @PostMapping("/audio")
    public ResultDTO<String> uploadAudio(@PathVariable Long trackId,
                                         @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "오디오 파일이 없습니다");
        }
        try {
            String prefix = packagingService.packageTrack(trackId, file.getBytes(), file.getOriginalFilename());
            return ResultDTO.ok(prefix);
        } catch (java.io.IOException e) {
            throw new ApiException(ResultCode.INTERNAL_ERROR, "파일 읽기 실패: " + e.getMessage());
        }
    }

    @Operation(summary = "데모 풀트랙 패키징", description = "트랙의 데모 샘플(previewUrl)을 받아 암호화 HLS로 패키징한다(파이프라인 시연용).")
    @PostMapping("/audio/demo")
    public ResultDTO<String> packageDemo(@PathVariable Long trackId) {
        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND, "트랙을 찾을 수 없습니다"));
        String sampleUrl = track.getPreviewUrl();
        if (sampleUrl == null || sampleUrl.isBlank()) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "샘플 URL이 없는 트랙입니다");
        }
        byte[] audio = sampleDownloader.download(sampleUrl);
        String prefix = packagingService.packageTrack(trackId, audio, "demo.mp3");
        return ResultDTO.ok(prefix);
    }
}
