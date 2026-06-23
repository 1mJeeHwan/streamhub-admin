package org.streamhub.api.base.storage;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Object storage operations against the configured bucket (MinIO locally, S3 in prod).
 */
@Service
public class StorageService {

    private final S3Client s3Client;
    private final String bucket;
    private final String endpoint;
    private final String region;
    private final String publicBaseUrl;

    public StorageService(
            S3Client s3Client,
            @Value("${storage.bucket}") String bucket,
            @Value("${storage.endpoint:}") String endpoint,
            @Value("${storage.region}") String region,
            // Public base for serving stored objects. Defaults to the CloudFront domain already set
            // for HLS (app.hls.segment-base-url) — in prod the media bucket is private, so objects are
            // served via the API proxy (/pub/v1/media/file) reachable on that same CloudFront domain.
            @Value("${storage.public-base-url:${app.hls.segment-base-url:}}") String publicBaseUrl) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.endpoint = endpoint;
        this.region = region;
        this.publicBaseUrl = publicBaseUrl != null && publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
    }

    /**
     * Uploads a file under {@code <prefix>/<uuid><ext>} and returns the object key.
     *
     * @throws ApiException if the file is empty or the upload fails
     */
    public String upload(MultipartFile file, String prefix) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ResultCode.INVALID_PARAMETER, "업로드할 파일이 없습니다");
        }
        String key = prefix + "/" + UUID.randomUUID() + extension(file.getOriginalFilename());
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(file.getContentType())
                            .build(),
                    RequestBody.fromBytes(file.getBytes()));
        } catch (IOException | RuntimeException e) {
            throw new ApiException(ResultCode.INTERNAL_ERROR, "파일 업로드 실패: " + e.getMessage());
        }
        return key;
    }

    /** Uploads raw bytes at an explicit key (used by the HLS packager for segments + playlist). */
    public void putBytes(String key, byte[] data, String contentType) {
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromBytes(data));
        } catch (RuntimeException e) {
            throw new ApiException(ResultCode.INTERNAL_ERROR, "객체 업로드 실패: " + e.getMessage());
        }
    }

    /** Reads an object's bytes (used to serve/rewrite the stored HLS playlist). */
    public byte[] getBytes(String key) {
        try {
            return s3Client.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
        } catch (RuntimeException e) {
            throw new ApiException(ResultCode.NOT_FOUND, "객체를 찾을 수 없습니다: " + key);
        }
    }

    public void delete(String key) {
        if (!StringUtils.hasText(key)) {
            return;
        }
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    /** Public URL for an object key (MinIO path-style locally, virtual-hosted S3 in prod). */
    public String publicUrl(String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        // Already an absolute URL (e.g. seeded external image) → pass through unchanged.
        if (key.startsWith("http://") || key.startsWith("https://")) {
            return key;
        }
        // Prod: the bucket is private (only hls/* is fronted by CloudFront), so non-hls objects are
        // served through the public API proxy on the CloudFront domain. hls/* keeps its own CDN path.
        if (StringUtils.hasText(publicBaseUrl) && !key.startsWith("hls/")) {
            return publicBaseUrl + "/pub/v1/media/file?key="
                    + URLEncoder.encode(key, StandardCharsets.UTF_8);
        }
        // Local (MinIO): direct path-style access.
        if (StringUtils.hasText(endpoint)) {
            return endpoint + "/" + bucket + "/" + key;
        }
        return "https://" + bucket + ".s3." + region + ".amazonaws.com/" + key;
    }

    private String extension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }
}
