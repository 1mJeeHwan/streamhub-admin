package org.streamhub.api.v1.album.hls;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.streamhub.api.v1.album.entity.Track;
import org.streamhub.api.v1.album.repository.TrackRepository;

/**
 * Bulk-encrypts every already-registered track that has a demo sample but no packaged full track.
 *
 * <p>Each eligible track's {@code previewUrl} is downloaded (through the SSRF-guarded
 * {@link HlsSampleDownloader}) and packaged into encrypted HLS via {@link HlsPackagingService}. The
 * batch runs asynchronously ({@link Async}) and strictly sequentially: the deploy box is a t3.micro
 * (~1 GB RAM), so two concurrent {@code ffmpeg} processes would OOM. Processing is idempotent —
 * already-packaged tracks are skipped — and continue-on-error, so one bad track never aborts the
 * batch.
 */
@Slf4j
@Service
public class HlsBatchPackagingService {

    private final TrackRepository trackRepository;
    private final HlsPackagingService packagingService;
    private final HlsSampleDownloader sampleDownloader;

    public HlsBatchPackagingService(TrackRepository trackRepository,
                                    HlsPackagingService packagingService,
                                    HlsSampleDownloader sampleDownloader) {
        this.trackRepository = trackRepository;
        this.packagingService = packagingService;
        this.sampleDownloader = sampleDownloader;
    }

    /**
     * Returns the ids of all tracks eligible for bulk encryption (unpackaged, non-blank previewUrl).
     * The endpoint calls this synchronously to report how many tracks were queued before kicking off
     * {@link #packageAllAsync(List)}.
     */
    public List<Long> eligibleTrackIds() {
        return trackRepository.findUnpackagedTrackIdsWithPreview();
    }

    /**
     * Packages each track in {@code trackIds} sequentially, on a background thread. Re-checks each
     * track at processing time (skipping any already packaged, so the batch is safe to re-run) and
     * continues past failures, logging progress per track.
     *
     * @param trackIds the snapshot of eligible track ids captured when the batch was queued
     */
    @Async
    public void packageAllAsync(List<Long> trackIds) {
        int total = trackIds.size();
        int done = 0;
        int skipped = 0;
        int failed = 0;
        log.info("Bulk HLS packaging started: {} track(s) queued", total);
        for (int i = 0; i < total; i++) {
            Long trackId = trackIds.get(i);
            try {
                if (packageOne(trackId)) {
                    done++;
                    log.info("Bulk HLS packaging progress: {}/{} (track {} packaged)", i + 1, total, trackId);
                } else {
                    skipped++;
                    log.info("Bulk HLS packaging progress: {}/{} (track {} skipped, already packaged or no sample)",
                            i + 1, total, trackId);
                }
            } catch (RuntimeException e) {
                failed++;
                log.warn("Bulk HLS packaging: track {} failed, continuing — {}", trackId, e.getMessage());
            }
        }
        log.info("Bulk HLS packaging finished: {} packaged, {} skipped, {} failed (of {} queued)",
                done, skipped, failed, total);
    }

    /**
     * Packages a single track if still eligible. Returns {@code true} if it was packaged, {@code false}
     * if it was skipped (gone, already packaged, or missing previewUrl).
     */
    private boolean packageOne(Long trackId) {
        Track track = trackRepository.findById(trackId).orElse(null);
        if (track == null || track.isHasFullTrack()) {
            return false;
        }
        String sampleUrl = track.getPreviewUrl();
        if (sampleUrl == null || sampleUrl.isBlank()) {
            return false;
        }
        byte[] audio = sampleDownloader.download(sampleUrl);
        packagingService.packageTrack(trackId, audio, "bulk-track-" + trackId + ".mp3");
        return true;
    }
}
