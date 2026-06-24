package org.streamhub.api.v1.chat.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.streamhub.api.v1.chat.entity.ChatMessage;
import org.streamhub.api.v1.chat.entity.ChatRole;

/** JPA repository for {@link ChatMessage} (C5). */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findBySessionIdOrderByCreatedAtAscIdAsc(Long sessionId);

    long countByRole(ChatRole role);

    /** BOT-reply counts grouped by intent — [ChatIntent, Long] rows for the analytics dashboard. */
    @Query("select m.intent, count(m) from ChatMessage m "
            + "where m.role = org.streamhub.api.v1.chat.entity.ChatRole.BOT group by m.intent")
    List<Object[]> countByIntent();
}
