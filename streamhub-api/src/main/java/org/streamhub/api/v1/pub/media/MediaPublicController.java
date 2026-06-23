package org.streamhub.api.v1.pub.media;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import java.util.Set;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;
import org.streamhub.api.base.storage.StorageService;

/**
 * Public read-through proxy for stored media objects ({@code /pub/v1/media/file?key=...}). The media
 * bucket is private (only {@code hls/*} is fronted by CloudFront), so uploaded images are served by
 * streaming them through the API, which reads the private bucket via its IAM role. {@code hls/*} keys
 * are rejected — encrypted segments have their own gated path.
 */
@Tag(name = "Media (public)", description = "업로드 미디어 공개 서빙")
@RestController
@RequestMapping("/pub/v1/media")
public class MediaPublicController {

    /**
     * First path segment of every key this proxy is allowed to serve. These are exactly the prefixes
     * under which images are uploaded ({@code StorageService.upload(file, prefix)}): media library
     * ({@code media/<category>}), church/content/goods/album thumbnails. Anything else — including
     * {@code hls/*} (its own gated path) and log archives — is rejected as NOT_FOUND so the private
     * bucket cannot be read object-by-object through this endpoint.
     */
    private static final Set<String> ALLOWED_PREFIXES =
            Set.of("media", "church", "content", "goods", "album");

    private final StorageService storageService;

    public MediaPublicController(StorageService storageService) {
        this.storageService = storageService;
    }

    @Operation(summary = "미디어 파일", description = "저장된 미디어 객체(이미지 등)를 키로 스트리밍한다.")
    @GetMapping("/file")
    public ResponseEntity<byte[]> file(@RequestParam("key") String key) {
        if (!StringUtils.hasText(key)
                || key.startsWith("hls/")
                || key.contains("..")
                || !isAllowed(key)) {
            throw new ApiException(ResultCode.NOT_FOUND);
        }
        byte[] bytes = storageService.getBytes(key);
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(contentType(key))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic());
        // SVGs can carry inline <script>; never let one render in the browser context. Force download.
        if (key.toLowerCase().endsWith(".svg")) {
            builder.header(HttpHeaders.CONTENT_DISPOSITION, "attachment");
        }
        return builder.body(bytes);
    }

    /** True only when the key's first path segment is one of {@link #ALLOWED_PREFIXES}. */
    private boolean isAllowed(String key) {
        int slash = key.indexOf('/');
        if (slash <= 0) {
            return false;
        }
        return ALLOWED_PREFIXES.contains(key.substring(0, slash));
    }

    private MediaType contentType(String key) {
        String k = key.toLowerCase();
        if (k.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (k.endsWith(".jpg") || k.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        if (k.endsWith(".gif")) {
            return MediaType.IMAGE_GIF;
        }
        if (k.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        if (k.endsWith(".svg")) {
            return MediaType.parseMediaType("image/svg+xml");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
