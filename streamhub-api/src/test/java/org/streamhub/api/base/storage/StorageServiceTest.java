package org.streamhub.api.base.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;

/** publicUrl resolution: prod proxy for media, raw S3 for hls, passthrough for external, MinIO local. */
@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    @Mock
    private S3Client s3Client;

    private StorageService service(String endpoint, String publicBase) {
        return new StorageService(s3Client, "bkt", endpoint, "ap-northeast-2", publicBase);
    }

    @Test
    void prodMediaKey_servedViaApiProxy() {
        assertThat(service("", "https://cdn.example.com").publicUrl("media/banner/a.png"))
                .isEqualTo("https://cdn.example.com/pub/v1/media/file?key=media%2Fbanner%2Fa.png");
    }

    @Test
    void hlsKey_notProxied_keepsOwnCdnPath() {
        assertThat(service("", "https://cdn.example.com").publicUrl("hls/track-1/seg000.ts"))
                .isEqualTo("https://bkt.s3.ap-northeast-2.amazonaws.com/hls/track-1/seg000.ts");
    }

    @Test
    void externalUrl_passesThrough() {
        assertThat(service("", "https://cdn.example.com").publicUrl("https://x.com/a.png"))
                .isEqualTo("https://x.com/a.png");
    }

    @Test
    void localMinioEndpoint_directPath() {
        assertThat(service("http://localhost:9000", "").publicUrl("media/a.png"))
                .isEqualTo("http://localhost:9000/bkt/media/a.png");
    }
}
