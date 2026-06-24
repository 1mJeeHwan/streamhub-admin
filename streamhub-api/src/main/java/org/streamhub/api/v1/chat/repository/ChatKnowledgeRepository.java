package org.streamhub.api.v1.chat.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.streamhub.api.v1.chat.entity.ChatKnowledge;

/** JPA repository for admin-managed chatbot knowledge (FAQ) entries. */
public interface ChatKnowledgeRepository extends JpaRepository<ChatKnowledge, Long> {

    /** All entries in display order (sortOrder asc, then id asc). */
    List<ChatKnowledge> findAllByOrderBySortOrderAscIdAsc();

    /** Enabled entries only, in display order — used by the chat providers at answer time. */
    List<ChatKnowledge> findByEnabledTrueOrderBySortOrderAscIdAsc();
}
