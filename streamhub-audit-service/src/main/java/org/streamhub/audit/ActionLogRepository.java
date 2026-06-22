package org.streamhub.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * JPA repository for {@link ActionLog} — this service's own audit store. Backs both the write side
 * (the Kafka consumer) and the read side (the {@code /v1/action-logs} query API the monolith calls
 * instead of touching the audit table directly).
 */
public interface ActionLogRepository extends JpaRepository<ActionLog, Long> {

    /**
     * Filtered, paged audit search. {@code action} is an exact match; {@code keyword} is a LIKE over
     * operator name / detail / target id. Null filters are ignored. Newest first.
     */
    @Query(value = "select a from ActionLog a where "
            + "(:action is null or a.action = :action) and "
            + "(:keyword is null or a.adminName like concat('%', :keyword, '%') "
            + "or a.detail like concat('%', :keyword, '%') "
            + "or a.targetId like concat('%', :keyword, '%')) "
            + "order by a.createdAt desc, a.id desc",
            countQuery = "select count(a) from ActionLog a where "
            + "(:action is null or a.action = :action) and "
            + "(:keyword is null or a.adminName like concat('%', :keyword, '%') "
            + "or a.detail like concat('%', :keyword, '%') "
            + "or a.targetId like concat('%', :keyword, '%'))")
    Page<ActionLog> search(@Param("action") String action,
                           @Param("keyword") String keyword,
                           Pageable pageable);
}
