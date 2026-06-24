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
 * A user question the bot could not answer (intent = FALLBACK). Collected so an operator can review
 * the gaps and turn them into knowledge — the learning loop that pairs with {@link ChatKnowledge}.
 * Marked {@code resolved} once handled (added to the knowledge base or dismissed).
 */
@Entity
@Table(name = "CHAT_UNANSWERED", indexes = {
        @Index(name = "idx_chat_unanswered_resolved", columnList = "resolved, created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatUnanswered {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "question", nullable = false, length = 2000)
    private String question;

    @Column(name = "session_key", length = 40)
    private String sessionKey;

    @Column(name = "resolved", nullable = false)
    private boolean resolved;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private ChatUnanswered(String question, String sessionKey) {
        this.question = question;
        this.sessionKey = sessionKey;
        this.resolved = false;
        this.createdAt = LocalDateTime.now();
    }

    public void resolve() {
        this.resolved = true;
    }
}
