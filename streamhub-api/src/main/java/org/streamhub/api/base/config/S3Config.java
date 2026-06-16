package org.streamhub.api.base.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * S3 client. The same code targets MinIO locally and real S3 in production —
 * only configuration differs:
 *
 * <ul>
 *   <li>local/docker: {@code storage.endpoint} is set → endpoint override + static
 *       MinIO credentials + path-style access.</li>
 *   <li>prod: {@code storage.endpoint} is empty → default client; credentials come
 *       from the EC2 IAM instance role.</li>
 * </ul>
 */
@Configuration
public class S3Config {

    @Value("${storage.endpoint:}")
    private String endpoint;

    @Value("${storage.region}")
    private String region;

    @Value("${storage.access-key:}")
    private String accessKey;

    @Value("${storage.secret-key:}")
    private String secretKey;

    @Bean
    public S3Client s3Client() {
        if (StringUtils.hasText(endpoint)) {
            // MinIO (local/docker)
            return S3Client.builder()
                    .endpointOverride(URI.create(endpoint))
                    .region(Region.of(region))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKey, secretKey)))
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(true)
                            .build())
                    .build();
        }
        // Real S3 (prod) — credentials from the default provider chain (IAM role).
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
