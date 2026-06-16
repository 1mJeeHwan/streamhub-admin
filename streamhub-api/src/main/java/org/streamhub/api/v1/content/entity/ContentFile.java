package org.streamhub.api.v1.content.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A file uploaded to object storage and attached to a {@link Content}. */
@Entity
@Table(name = "CONTENT_FILE", indexes = {
        @Index(name = "idx_content_file_content", columnList = "content_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "content_id", nullable = false)
    private Long contentId;

    @Column(name = "s3_key", nullable = false, length = 300)
    private String s3Key;

    @Column(name = "file_type", length = 50)
    private String fileType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Builder
    private ContentFile(Long contentId, String s3Key, String fileType, Long sizeBytes) {
        this.contentId = contentId;
        this.s3Key = s3Key;
        this.fileType = fileType;
        this.sizeBytes = sizeBytes;
    }
}
