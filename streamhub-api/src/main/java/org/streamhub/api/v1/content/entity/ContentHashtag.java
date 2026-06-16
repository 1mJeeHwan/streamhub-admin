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

/** Join row linking a {@link Content} to a {@link Hashtag}. */
@Entity
@Table(name = "CONTENT_HASHTAG", indexes = {
        @Index(name = "idx_content_hashtag_content", columnList = "content_id"),
        @Index(name = "idx_content_hashtag_tag", columnList = "hashtag_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentHashtag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "content_id", nullable = false)
    private Long contentId;

    @Column(name = "hashtag_id", nullable = false)
    private Long hashtagId;

    @Builder
    private ContentHashtag(Long contentId, Long hashtagId) {
        this.contentId = contentId;
        this.hashtagId = hashtagId;
    }
}
