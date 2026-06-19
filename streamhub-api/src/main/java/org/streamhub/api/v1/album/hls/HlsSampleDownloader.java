package org.streamhub.api.v1.album.hls;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;
import org.streamhub.api.base.exception.ApiException;
import org.streamhub.api.base.response.ResultCode;

/**
 * Fetches an external audio sample ({@code previewUrl}) server-side, behind the {@link SsrfGuard}.
 *
 * <p>Centralizes the only server-side outbound HTTP fetch in the HLS package so the SSRF defenses
 * (URL allow-list validation + disabled redirects) live in one place and cannot be bypassed by a
 * second, unguarded call site. Redirects are disabled ({@link HttpClient.Redirect#NEVER}) so an
 * allowed public host cannot 302 to a blocked internal address after validation.
 */
@Component
public class HlsSampleDownloader {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /**
     * Downloads the bytes at {@code url} after validating it with {@link SsrfGuard}.
     *
     * @param url the external sample URL to fetch
     * @return the downloaded bytes
     * @throws ApiException {@code INVALID_PARAMETER} if the URL is not allowed,
     *     or {@code INTERNAL_ERROR} if the fetch fails or returns a non-200 status
     */
    public byte[] download(String url) {
        URI uri = SsrfGuard.validate(url);
        try {
            HttpResponse<byte[]> response = httpClient.send(
                    HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new ApiException(ResultCode.INTERNAL_ERROR,
                        "샘플 다운로드 실패 (HTTP " + response.statusCode() + ")");
            }
            return response.body();
        } catch (java.io.IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ApiException(ResultCode.INTERNAL_ERROR, "샘플 다운로드 실패: " + e.getMessage());
        }
    }
}
