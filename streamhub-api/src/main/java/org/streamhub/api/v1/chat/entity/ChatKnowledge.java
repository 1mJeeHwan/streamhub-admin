package org.streamhub.api.v1.chat.entity;

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
 * An admin-managed chatbot knowledge entry (FAQ). The admin "teaches" the bot by adding rows here:
 * the rule provider matches {@link #keyword} against the user message and returns {@link #answer};
 * the LLM provider injects active entries into its system prompt as authoritative context. Replaces
 * the old hard-coded FAQ table so answers can be edited without a deploy.
 */
@Entity
@Table(name = "CHAT_KNOWLEDGE", indexes = {
        @Index(name = "idx_chat_knowledge_enabled", columnList = "enabled")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatKnowledge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Short question/topic shown in the admin list (e.g. "배송비"). */
    @Column(name = "question", nullable = false, length = 200)
    private String question;

    /** Space/comma-separated trigger terms matched (lowercased, contains) against the user message. */
    @Column(name = "keywords", nullable = false, length = 300)
    private String keywords;

    @Column(name = "answer", nullable = false, length = 1000)
    private String answer;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private ChatKnowledge(String question, String keywords, String answer, boolean enabled,
                          int sortOrder) {
        this.question = question;
        this.keywords = keywords;
        this.answer = answer;
        this.enabled = enabled;
        this.sortOrder = sortOrder;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void update(String question, String keywords, String answer, boolean enabled,
                       int sortOrder) {
        this.question = question;
        this.keywords = keywords;
        this.answer = answer;
        this.enabled = enabled;
        this.sortOrder = sortOrder;
        this.updatedAt = LocalDateTime.now();
    }
}
