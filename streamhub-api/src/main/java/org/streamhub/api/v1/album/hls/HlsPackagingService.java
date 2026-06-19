package org.streamhub.api.v1.album.hls;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.base.storage.StorageService;
import org.streamhub.api.v1.album.entity.Track;
import org.streamhub.api.v1.album.repository.TrackRepository;

/**
 * Packages a full-track audio file into an AES-128 encrypted HLS stream and stores it.
 *
 * <p>Pipeline: generate a random AES-128 key + IV → run {@code ffmpeg} to transcode (AAC 128k) and
 * segment the audio, encrypting each {@code .ts} segment → upload the encrypted segments + the
 * {@code index.m3u8} to S3 under {@code hls/track-{id}/} → persist the key in {@code TRACK_HLS_KEY}
 * (server-only). The encrypted segments are safe to serve from a public CDN; only the key is gated.
 *
 * <p>{@code ffmpeg} must be on the runtime (installed in the deploy image).
 */
@Slf4j
@Service
public class HlsPackagingService {

    private static final HexFormat HEX = HexFormat.of();
    private static final int HLS_SEGMENT_SECONDS = 6;
    private static final long FFMPEG_TIMEOUT_SECONDS = 180;

    private final TrackRepository trackRepository;
    private final HlsKeyRepository hlsKeyRepository;
    private final StorageService storageService;
    private final String ffmpegPath;
    private final SecureRandom random = new SecureRandom();

    public HlsPackagingService(TrackRepository trackRepository,
                               HlsKeyRepository hlsKeyRepository,
                               StorageService storageService,
                               @Value("${app.hls.ffmpeg-path:ffmpeg}") String ffmpegPath) {
        this.trackRepository = trackRepository;
        this.hlsKeyRepository = hlsKeyRepository;
        this.storageService = storageService;
        this.ffmpegPath = ffmpegPath;
    }

    /**
     * Packages {@code audio} as the encrypted full track for {@code trackId}.
     *
     * @return the S3 prefix the HLS assets were stored under
     * @throws ApiException if the track is missing, ffmpeg is unavailable/fails, or upload fails
     */
    @Transactional
    public String packageTrack(Long trackId, byte[] audio, String sourceFilename) {
        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new ApiException(ResultCode.NOT_FOUND, "트랙을 찾을 수 없습니다"));
        if (audio == null || audio.length == 0) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "오디오 파일이 비어 있습니다");
        }

        byte[] keyBytes = new byte[16];
        byte[] ivBytes = new byte[16];
        random.nextBytes(keyBytes);
        random.nextBytes(ivBytes);
        String keyHex = HEX.formatHex(keyBytes);
        String ivHex = HEX.formatHex(ivBytes);

        Path work = null;
        try {
            work = Files.createTempDirectory("hls-" + trackId + "-");
            // Build the temp input name from a sanitized extension rather than the raw upload name:
            // a crafted name (e.g. "x.mp3/../../../tmp/evil") would otherwise let work.resolve(...)
            // escape the temp dir and write an arbitrary file before ffmpeg.
            Path input = work.resolve("input" + safeExtension(sourceFilename));
            Files.write(input, audio);

            // ffmpeg key_info file: line1 = key URI written into the playlist (rewritten when served),
            // line2 = path to the raw key bytes ffmpeg encrypts with, line3 = IV (hex).
            Path keyFile = work.resolve("enc.key");
            Files.write(keyFile, keyBytes);
            Path keyInfo = work.resolve("keyinfo.txt");
            Files.writeString(keyInfo, "enc.key\n" + keyFile.toAbsolutePath() + "\n" + ivHex + "\n");

            runFfmpeg(work, input, keyInfo);

            String prefix = "hls/track-" + trackId + "/";
            int durationSec = uploadHlsAssets(work, prefix);

            HlsKey saved = hlsKeyRepository.save(HlsKey.builder()
                    .trackId(trackId).keyHex(keyHex).ivHex(ivHex).build());
            track.attachHls(prefix, saved.getId(), durationSec);
            trackRepository.save(track);

            log.info("Packaged encrypted HLS for track {} from '{}' ({} s) under {}",
                    trackId, sourceFilename, durationSec, prefix);
            return prefix;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ApiException(ResultCode.INTERNAL_ERROR, "HLS 패키징 실패: " + e.getMessage());
        } finally {
            cleanup(work);
        }
    }

    /**
     * Packages {@code audio} as a <b>public, unencrypted</b> HLS stream under {@code prefix} (no AES
     * key — the playlist and segments are freely cacheable). Used for free music with no purchase
     * gate: album previews and audio content. The caller persists {@code prefix} on its own entity.
     *
     * @param prefix        S3 key prefix to store the assets under (e.g. {@code hls/preview/track-7/})
     * @param maxSeconds     if {@code > 0}, only the first {@code maxSeconds} are packaged (preview clip)
     * @return total duration of the packaged stream (seconds)
     * @throws ApiException if the audio is empty, or ffmpeg is unavailable/fails, or upload fails
     */
    public int packagePublic(byte[] audio, String prefix, String sourceFilename, int maxSeconds) {
        if (audio == null || audio.length == 0) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "오디오 파일이 비어 있습니다");
        }
        Path work = null;
        try {
            work = Files.createTempDirectory("hls-pub-");
            Path input = work.resolve("input" + safeExtension(sourceFilename));
            Files.write(input, audio);
            runFfmpegPublic(work, input, maxSeconds);
            int durationSec = uploadHlsAssets(work, prefix);
            log.info("Packaged public HLS from '{}' ({} s) under {}", sourceFilename, durationSec, prefix);
            return durationSec;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ApiException(ResultCode.INTERNAL_ERROR, "HLS 패키징 실패: " + e.getMessage());
        } finally {
            cleanup(work);
        }
    }

    private void runFfmpeg(Path work, Path input, Path keyInfo) throws IOException, InterruptedException {
        execFfmpeg(work, List.of(
                ffmpegPath, "-y",
                "-i", input.toAbsolutePath().toString(),
                "-vn", "-c:a", "aac", "-b:a", "128k", "-ar", "44100",
                "-f", "hls",
                "-hls_time", String.valueOf(HLS_SEGMENT_SECONDS),
                "-hls_playlist_type", "vod",
                "-hls_key_info_file", keyInfo.toAbsolutePath().toString(),
                "-hls_segment_filename", "seg%03d.ts",
                "index.m3u8"));
    }

    /**
     * Plain (unencrypted) HLS transcode — no {@code -hls_key_info_file}, so segments are not
     * encrypted. {@code maxSeconds > 0} caps the output (e.g. a 30-second preview clip).
     */
    private void runFfmpegPublic(Path work, Path input, int maxSeconds)
            throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>(List.of(
                ffmpegPath, "-y",
                "-i", input.toAbsolutePath().toString(),
                "-vn", "-c:a", "aac", "-b:a", "128k", "-ar", "44100"));
        if (maxSeconds > 0) {
            cmd.add("-t");
            cmd.add(String.valueOf(maxSeconds));
        }
        cmd.addAll(List.of(
                "-f", "hls",
                "-hls_time", String.valueOf(HLS_SEGMENT_SECONDS),
                "-hls_playlist_type", "vod",
                "-hls_segment_filename", "seg%03d.ts",
                "index.m3u8"));
        execFfmpeg(work, cmd);
    }

    /** Runs an ffmpeg command in {@code work}, enforcing the timeout and a zero exit code. */
    private void execFfmpeg(Path work, List<String> cmd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(work.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean finished = process.waitFor(FFMPEG_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new ApiException(ResultCode.INTERNAL_ERROR, "ffmpeg 시간 초과");
        }
        if (process.exitValue() != 0) {
            log.warn("ffmpeg failed: {}", output);
            throw new ApiException(ResultCode.INTERNAL_ERROR,
                    "ffmpeg 변환 실패 (exit " + process.exitValue() + ")");
        }
    }

    /** Uploads index.m3u8 + segments to S3; returns total duration (sum of EXTINF, seconds). */
    private int uploadHlsAssets(Path work, String prefix) throws IOException {
        Path playlist = work.resolve("index.m3u8");
        if (!Files.exists(playlist)) {
            throw new ApiException(ResultCode.INTERNAL_ERROR, "ffmpeg 결과 플레이리스트가 없습니다");
        }
        String m3u8 = Files.readString(playlist, StandardCharsets.UTF_8);
        storageService.putBytes(prefix + "index.m3u8", m3u8.getBytes(StandardCharsets.UTF_8),
                "application/vnd.apple.mpegurl");

        try (var segments = Files.list(work)) {
            List<Path> tsFiles = segments
                    .filter(p -> p.getFileName().toString().endsWith(".ts"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
            if (tsFiles.isEmpty()) {
                throw new ApiException(ResultCode.INTERNAL_ERROR, "ffmpeg 세그먼트가 생성되지 않았습니다");
            }
            for (Path ts : tsFiles) {
                storageService.putBytes(prefix + ts.getFileName(), Files.readAllBytes(ts), "video/mp2t");
            }
        }
        return totalDurationSeconds(m3u8);
    }

    private int totalDurationSeconds(String m3u8) {
        double total = 0;
        for (String line : m3u8.split("\n")) {
            if (line.startsWith("#EXTINF:")) {
                String value = line.substring("#EXTINF:".length()).split(",")[0].trim();
                try {
                    total += Double.parseDouble(value);
                } catch (NumberFormatException ignored) {
                    // skip malformed EXTINF
                }
            }
        }
        return (int) Math.round(total);
    }

    private static final Pattern SAFE_EXTENSION = Pattern.compile("^\\.[A-Za-z0-9]{1,5}$");
    private static final String DEFAULT_EXTENSION = ".mp3";

    /**
     * Returns a safe extension for the temp input file. The raw upload name is untrusted, so the
     * extension is accepted only if it matches {@code ^\.[A-Za-z0-9]{1,5}$} (no {@code /}, {@code \},
     * or {@code ..}); anything else defaults to {@value #DEFAULT_EXTENSION}. This neutralizes path
     * traversal in the temp input path (e.g. {@code x.mp3/../../../tmp/evil}).
     */
    static String safeExtension(String sourceFilename) {
        if (sourceFilename == null) {
            return DEFAULT_EXTENSION;
        }
        int dot = sourceFilename.lastIndexOf('.');
        if (dot < 0) {
            return DEFAULT_EXTENSION;
        }
        String ext = sourceFilename.substring(dot);
        return SAFE_EXTENSION.matcher(ext).matches() ? ext : DEFAULT_EXTENSION;
    }

    private void cleanup(Path dir) {
        if (dir == null) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best-effort temp cleanup
                }
            });
        } catch (IOException ignored) {
            // best-effort temp cleanup
        }
    }

}
