package org.streamhub.api.v1.album.hls;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.streamhub.api.base.response.ResultDTO;
import org.streamhub.api.v1.album.repository.TrackRepository;

/**
 * Admin endpoints to bulk-encrypt every already-registered track into encrypted HLS, and to poll
 * the progress.
 *
 * <ul>
 *   <li>{@code POST /v1/album/hls/package-all} — queue all unpackaged tracks (non-blank previewUrl)
 *       for encryption and return immediately with {@code {queued: N}}; the work runs async and
 *       sequentially in the background.</li>
 *   <li>{@code GET /v1/album/hls/package-status} — {@code {total, packaged, remaining}} for polling.</li>
 * </ul>
 *
 * <p>Same authorization as {@link AlbumHlsAdminController} (SYSTEM / CHURCH_MANAGER).
 */
@Tag(name = "Album HLS Admin", description = "암호화 풀트랙 일괄 패키징 (관리자)")
@RestController
@RequestMapping("/v1/album/hls")
@PreAuthorize("hasAnyAuthority(T(org.streamhub.api.base.security.AuthoritiesConstants).SYSTEM, "
        + "T(org.streamhub.api.base.security.AuthoritiesConstants).CHURCH_MANAGER)")
public class HlsBatchPackagingController {

    private final HlsBatchPackagingService batchService;
    private final TrackRepository trackRepository;

    public HlsBatchPackagingController(HlsBatchPackagingService batchService,
                                       TrackRepository trackRepository) {
        this.batchService = batchService;
        this.trackRepository = trackRepository;
    }

    @Operation(summary = "전체 트랙 일괄 암호화",
            description = "미패키징 트랙(previewUrl 보유)을 모두 암호화 HLS로 변환하는 작업을 비동기·순차로 시작하고 즉시 반환한다.")
    @PostMapping("/package-all")
    public ResultDTO<Map<String, Integer>> packageAll() {
        List<Long> trackIds = batchService.eligibleTrackIds();
        batchService.packageAllAsync(trackIds);
        return ResultDTO.ok(Map.of("queued", trackIds.size()));
    }

    @Operation(summary = "일괄 암호화 진행 상태",
            description = "전체/완료/남은 트랙 수를 반환해 진행률을 폴링할 수 있게 한다.")
    @GetMapping("/package-status")
    public ResultDTO<HlsPackageStatus> packageStatus() {
        long packaged = trackRepository.countByHasFullTrackTrue();
        long remaining = trackRepository.countUnpackagedWithPreview();
        return ResultDTO.ok(new HlsPackageStatus(packaged + remaining, packaged, remaining));
    }
}
