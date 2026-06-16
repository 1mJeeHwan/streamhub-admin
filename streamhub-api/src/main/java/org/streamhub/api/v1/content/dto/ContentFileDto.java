package org.streamhub.api.v1.content.dto;

import org.streamhub.api.v1.content.entity.ContentFile;

/**
 * An attached file with its public URL.
 *
 * @param id        file id
 * @param s3Key     storage key
 * @param url       public URL
 * @param fileType  MIME type
 * @param sizeBytes size in bytes
 */
public record ContentFileDto(Long id, String s3Key, String url, String fileType, Long sizeBytes) {

    public static ContentFileDto of(ContentFile file, String url) {
        return new ContentFileDto(file.getId(), file.getS3Key(), url, file.getFileType(), file.getSizeBytes());
    }
}
