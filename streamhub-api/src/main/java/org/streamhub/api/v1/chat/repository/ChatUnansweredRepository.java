package org.streamhub.api.v1.chat.repository;

import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.streamhub.api.v1.chat.entity.ChatUnanswered;

/** JPA repository for unanswered (FALLBACK) user questions — the chatbot learning queue. */
public interface ChatUnansweredRepository extends JpaRepository<ChatUnanswered, Long> {

    /** Open (unresolved) questions, newest first, capped. */
    List<ChatUnanswered> findByResolvedFalseOrderByCreatedAtDesc(Limit limit);

    long countByResolvedFalse();
}
