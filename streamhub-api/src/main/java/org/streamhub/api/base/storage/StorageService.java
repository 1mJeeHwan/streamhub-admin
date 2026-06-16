package org.streamhub.api.base.storage;

import java.io.IOException;
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

    public StorageService(
            S3Client s3Client,
            @Value("${storage.bucket}") String bucket,
            @Value("${storage.endpoint:}") String endpoint,
            @Value("${storage.region}") String region) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.endpoint = endpoint;
        this.region = region;
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
