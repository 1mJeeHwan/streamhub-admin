package org.streamhub.api.v1.analytics.repository;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.streamhub.api.v1.analytics.entity.AnalyticsEvent;
import org.streamhub.api.v1.analytics.entity.ContentKind;
import org.streamhub.api.v1.analytics.entity.DeviceKind;
import org.streamhub.api.v1.analytics.entity.EventType;

/**
 * JPA repository for {@link org.streamhub.api.v1.analytics.entity.AnalyticsEvent} (web-analytics
 * events).
 *
 * <p>The dashboard aggregates are computed with grouped JPQL (not {@code findAll()} into memory) so a
 * dashboard hit never loads the whole {@code ANALYTICS_EVENT} table. Every aggregate is bounded by an
 * {@code occurredAt >= :since} window supplied by the service.
 */
public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, Long> {

    List<AnalyticsEvent> findByOccurredAtBetween(LocalDateTime from, LocalDateTime to);

    /** Total event rows within the window. */
    @Query("SELECT COUNT(e) FROM AnalyticsEvent e WHERE e.occurredAt >= :since")
    long countSince(@Param("since") LocalDateTime since);

    /** Distinct non-null session ids within the window. */
    @Query("SELECT COUNT(DISTINCT e.sessionId) FROM AnalyticsEvent e "
            + "WHERE e.occurredAt >= :since AND e.sessionId IS NOT NULL")
    long countDistinctSessionsSince(@Param("since") LocalDateTime since);

    /** Distinct non-null member ids (unique signed-in visitors) within the window. */
    @Query("SELECT COUNT(DISTINCT e.memberId) FROM AnalyticsEvent e "
            + "WHERE e.occurredAt >= :since AND e.memberId IS NOT NULL")
    long countDistinctVisitorsSince(@Param("since") LocalDateTime since);

    /** Event count for a single {@link EventType} within the window. */
    @Query("SELECT COUNT(e) FROM AnalyticsEvent e WHERE e.occurredAt >= :since AND e.type = :type")
    long countByTypeSince(@Param("since") LocalDateTime since, @Param("type") EventType type);

    /** Mean dwell time (ms) over events that report one, within the window; {@code null} if none. */
    @Query("SELECT AVG(e.dwellMs) FROM AnalyticsEvent e "
            + "WHERE e.occurredAt >= :since AND e.dwellMs IS NOT NULL")
    Double avgDwellMsSince(@Param("since") LocalDateTime since);

    /**
     * Per-content view aggregates grouped by {@code (contentType, targetId)} over CONTENT_VIEW events
     * with a target, ordered by view count descending. Title is taken as the max (denormalized and
     * stable per target).
     */
    @Query("SELECT e.contentType AS contentType, e.targetId AS targetId, MAX(e.title) AS title, "
            + "COUNT(e) AS views, AVG(e.dwellMs) AS avgDwellMs, MAX(e.occurredAt) AS lastViewedAt "
            + "FROM AnalyticsEvent e "
            + "WHERE e.occurredAt >= :since AND e.type = org.streamhub.api.v1.analytics.entity.EventType.CONTENT_VIEW "
            + "AND e.targetId IS NOT NULL "
            + "GROUP BY e.contentType, e.targetId "
            + "ORDER BY COUNT(e) DESC")
    List<ContentStatRow> contentPerformanceSince(@Param("since") LocalDateTime since);

    /** Event counts grouped by device kind within the window. */
    @Query("SELECT e.deviceType AS deviceType, COUNT(e) AS count FROM AnalyticsEvent e "
            + "WHERE e.occurredAt >= :since AND e.deviceType IS NOT NULL "
            + "GROUP BY e.deviceType")
    List<DeviceCountRow> deviceCountsSince(@Param("since") LocalDateTime since);

    /** Referrer counts (non-blank) within the window, ordered descending; cap with {@code Pageable}. */
    @Query("SELECT e.referrer AS label, COUNT(e) AS count FROM AnalyticsEvent e "
            + "WHERE e.occurredAt >= :since AND e.referrer IS NOT NULL AND e.referrer <> '' "
            + "GROUP BY e.referrer ORDER BY COUNT(e) DESC")
    List<LabelCountRow> topReferrersSince(@Param("since") LocalDateTime since,
                                          org.springframework.data.domain.Pageable pageable);

    /** Path counts (non-blank) within the window, ordered descending; cap with {@code Pageable}. */
    @Query("SELECT e.path AS label, COUNT(e) AS count FROM AnalyticsEvent e "
            + "WHERE e.occurredAt >= :since AND e.path IS NOT NULL AND e.path <> '' "
            + "GROUP BY e.path ORDER BY COUNT(e) DESC")
    List<LabelCountRow> topPathsSince(@Param("since") LocalDateTime since,
                                      org.springframework.data.domain.Pageable pageable);

    /** Projection for {@link #contentPerformanceSince}. */
    interface ContentStatRow {
        ContentKind getContentType();

        Long getTargetId();

        String getTitle();

        long getViews();

        Double getAvgDwellMs();

        LocalDateTime getLastViewedAt();
    }

    /** Projection for {@link #deviceCountsSince}. */
    interface DeviceCountRow {
        DeviceKind getDeviceType();

        long getCount();
    }

    /** Projection for {@link #topReferrersSince} / {@link #topPathsSince}. */
    interface LabelCountRow {
        String getLabel();

        long getCount();
    }
}
