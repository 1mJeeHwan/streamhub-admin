package org.streamhub.api.v1.chat.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.streamhub.api.v1.chat.entity.ChatSession;

/** JPA repository for {@link ChatSession} (C5). */
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    Optional<ChatSession> findBySessionKey(String sessionKey);

    /** Session counts grouped by provider — [provider, Long] rows for the analytics dashboard. */
    @Query("select s.provider, count(s) from ChatSession s group by s.provider")
    List<Object[]> countByProvider();

    /** Daily session counts since a date — [java.sql.Date, Long] rows for the volume chart. */
    @Query("select function('date', s.createdAt), count(s) from ChatSession s "
            + "where s.createdAt >= :since group by function('date', s.createdAt) "
            + "order by function('date', s.createdAt)")
    List<Object[]> dailySince(@Param("since") LocalDateTime since);
}
