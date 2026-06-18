package org.streamhub.api.v1.community.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A community post (게시글) belonging to a {@link Board}. {@code secretYn} hides the body from
 * other members. All values are demo/fictional (no real PII — PII guard).
 */
@Entity
@Table(name = "COMMUNITY_POST", indexes = {
        @Index(name = "idx_post_board", columnList = "board_id"),
        @Index(name = "idx_post_category", columnList = "category")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → BOARD. */
    @Column(name = "board_id", nullable = false)
    private Long boardId;

    @Column(name = "category", length = 40)
    private String category;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT", length = 2000)
    private String content;

    @Column(name = "writer_name", length = 60)
    private String writerName;

    @Column(name = "secret_yn", nullable = false, length = 1)
    private String secretYn;

    @Column(name = "recommend_count", nullable = false)
    private int recommendCount;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private CommunityPost(Long boardId, String category, String title, String content, String writerName,
                 String secretYn, int recommendCount, int viewCount, LocalDateTime createdAt) {
        this.boardId = boardId;
        this.category = category;
        this.title = title;
        this.content = content;
        this.writerName = writerName;
        this.secretYn = secretYn != null ? secretYn : "N";
        this.recommendCount = recommendCount;
        this.viewCount = viewCount;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    /**
     * Applies an editorial update to the mutable fields. Counters and {@code createdAt} are left
     * untouched — those are not author-editable.
     *
     * @param boardId    target board
     * @param category   category label (nullable)
     * @param title      post title
     * @param content    post body (nullable)
     * @param writerName display author name (nullable)
     * @param secretYn   {@code "Y"} to hide the body from other members; defaults to {@code "N"}
     */
    public void update(Long boardId, String category, String title, String content, String writerName,
                       String secretYn) {
        this.boardId = boardId;
        this.category = category;
        this.title = title;
        this.content = content;
        this.writerName = writerName;
        this.secretYn = secretYn != null ? secretYn : "N";
    }
}
