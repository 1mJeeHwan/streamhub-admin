package org.streamhub.api.v1.content.dto;

/**
 * Result of a file upload.
 *
 * @param key storage object key (store this on the content)
 * @param url public URL for previewing the uploaded object
 */
public record UploadResponse(String key, String url) {
}
