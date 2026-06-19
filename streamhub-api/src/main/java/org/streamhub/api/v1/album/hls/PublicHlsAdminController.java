package org.streamhub.api.v1.album.hls;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.streamhub.api.base.response.ResultDTO;

/**
 * Admin triggers to package free music into public HLS — single items and idempotent batch backfill.
 * Batch loops through the proxied {@link PublicHlsPackagingService} so each item runs in its own
 * transaction and a single failure (e.g. an unreachable sample URL) is logged and skipped rather
 * than aborting the run.
 */
@Slf4j
@Tag(name = "Public HLS Admin", description = "공개 음악 HLS 패키징 (관리자)")
@RestController
@PreAuthorize("hasAnyAuthority(T(org.streamhub.api.base.security.AuthoritiesConstants).SYSTEM, "
        + "T(org.streamhub.api.base.security.AuthoritiesConstants).CHURCH_MANAGER)")
public class PublicHlsAdminController {

    private final PublicHlsPackagingService packagingService;

    public PublicHlsAdminController(PublicHlsPackagingService packagingService) {
        this.packagingService = packagingService;
    }

    @Operation(summary = "프리뷰 HLS 패키징", description = "트랙의 미리듣기 소스를 30초 공개 HLS로 패키징한다.")
    @PostMapping("/v1/album/tracks/{trackId}/preview/package")
    public ResultDTO<String> packagePreview(@PathVariable Long trackId) {
        return ResultDTO.ok(packagingService.packagePreview(trackId));
    }

    @Operation(summary = "음원 콘텐츠 HLS 패키징", description = "음원(SOUND) 콘텐츠의 미디어를 공개 HLS로 패키징한다.")
    @PostMapping("/v1/content/{contentId}/audio/package")
    public ResultDTO<String> packageContent(@PathVariable Long contentId) {
        return ResultDTO.ok(packagingService.packageContent(contentId));
    }

    @Operation(summary = "전체 프리뷰 백필", description = "미패키징 프리뷰를 순차 패키징(멱등·실패 건너뜀). 처리/실패 건수 반환.")
    @PostMapping("/v1/album/previews/package-all")
    public ResultDTO<Map<String, Integer>> packageAllPreviews() {
        return ResultDTO.ok(runBatch(packagingService.previewCandidates(), packagingService::packagePreview, "preview"));
    }

    @Operation(summary = "전체 음원 콘텐츠 백필", description = "미패키징 음원 콘텐츠를 순차 패키징(멱등·실패 건너뜀). 처리/실패 건수 반환.")
    @PostMapping("/v1/content/audio/package-all")
    public ResultDTO<Map<String, Integer>> packageAllContentAudio() {
        return ResultDTO.ok(runBatch(packagingService.contentAudioCandidates(), packagingService::packageContent, "content"));
    }

    private Map<String, Integer> runBatch(List<Long> ids, java.util.function.Function<Long, String> packager, String label) {
        int ok = 0;
        int failed = 0;
        for (Long id : ids) {
            try {
                packager.apply(id);
                ok++;
            } catch (RuntimeException e) {
                failed++;
                log.warn("public HLS {} packaging failed for id {}: {}", label, id, e.getMessage());
            }
        }
        log.info("public HLS {} backfill: {} ok, {} failed of {}", label, ok, failed, ids.size());
        return Map.of("total", ids.size(), "packaged", ok, "failed", failed);
    }
}
