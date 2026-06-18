package org.streamhub.api.v1.analytics;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.streamhub.api.v1.analytics.dto.AnalyticsBreakdownDto;
import org.streamhub.api.v1.analytics.dto.AnalyticsOverviewDto;
import org.streamhub.api.v1.analytics.dto.ContentStatDto;
import org.streamhub.api.v1.analytics.dto.CountItemDto;
import org.streamhub.api.v1.analytics.dto.EventIngestRequest;
import org.streamhub.api.v1.analytics.dto.TimeseriesPointDto;
import org.streamhub.api.v1.analytics.entity.AnalyticsEvent;
import org.streamhub.api.v1.analytics.entity.ContentKind;
import org.streamhub.api.v1.analytics.entity.DeviceKind;
import org.streamhub.api.v1.analytics.entity.EventType;
import org.streamhub.api.v1.analytics.repository.AnalyticsEventRepository;

/**
 * Web-analytics pipeline (Firebase-style). The public side persists one cheap row per ingested
 * event, parsing the client-supplied enums defensively so malformed browser input never 500s. The
 * admin side computes every aggregate with grouped JPQL bounded by a look-back window, so a
 * dashboard hit never loads the whole {@code ANALYTICS_EVENT} table into memory.
 */
@Service
public class AnalyticsService {

    /** Look-back window for the timeseries trend. */
    private static final int TIMESERIES_DAYS = 30;

    /**
     * Look-back window (days) for the overview / content-performance / breakdown aggregates. Bounds
     * every grouped query so the dashboard cost is proportional to recent traffic, not table size.
     */
    private static final int AGGREGATE_DAYS = 90;

    /** Number of top referrers returned in the breakdown. */
    private static final int TOP_REFERRERS = 6;

    /** Number of top paths returned in the breakdown. */
    private static final int TOP_PATHS = 8;

    private final AnalyticsEventRepository analyticsEventRepository;

    public AnalyticsService(AnalyticsEventRepository analyticsEventRepository) {
        this.analyticsEventRepository = analyticsEventRepository;
    }

    /** Persists one ingested event, stamping {@code occurredAt=now()} and defaulting bad enums. */
    @Transactional
    public void ingest(EventIngestRequest request) {
        if (request == null) {
            return;
        }
        analyticsEventRepository.save(AnalyticsEvent.builder()
                .type(parseType(request.type()))
                .contentType(parseContentKind(request.contentType()))
                .targetId(request.targetId())
                .title(clamp(request.title(), 200))
                .path(clamp(request.path(), 200))
                .sessionId(clamp(request.sessionId(), 64))
                .memberId(request.memberId())
                .deviceType(parseDevice(request.deviceType()))
                .referrer(clamp(request.referrer(), 300))
                .dwellMs(request.dwellMs())
                .occurredAt(LocalDateTime.now())
                .build());
    }

    /** Persists a batch of ingested events; lenient, skips nulls. */
    @Transactional
    public void ingestBatch(List<EventIngestRequest> requests) {
        if (requests == null) {
            return;
        }
        for (EventIngestRequest request : requests) {
            ingest(request);
        }
    }

    /**
     * Overview over the last {@value #AGGREGATE_DAYS} days: totals, distinct sessions/visitors, view
     * split and average dwell. Computed entirely with grouped SQL — no table scan into memory.
     */
    @Transactional(readOnly = true)
    public AnalyticsOverviewDto overview() {
        LocalDateTime since = aggregateSince();

        long totalEvents = analyticsEventRepository.countSince(since);
        long totalSessions = analyticsEventRepository.countDistinctSessionsSince(since);
        long uniqueVisitors = analyticsEventRepository.countDistinctVisitorsSince(since);
        long pageViews = analyticsEventRepository.countByTypeSince(since, EventType.PAGE_VIEW);
        long contentViews = analyticsEventRepository.countByTypeSince(since, EventType.CONTENT_VIEW);
        long avgDwellMs = roundAvg(analyticsEventRepository.avgDwellMsSince(since));

        return new AnalyticsOverviewDto(
                totalEvents, totalSessions, uniqueVisitors, pageViews, contentViews, avgDwellMs);
    }

    /**
     * Per-content view stats, grouped by {@code (contentType, targetId)} over CONTENT_VIEW events
     * with a target, sorted by views descending. The frontend slices top (popular) and bottom
     * (underperforming) off this single list.
     */
    @Transactional(readOnly = true)
    public List<ContentStatDto> contentPerformance() {
        return analyticsEventRepository.contentPerformanceSince(aggregateSince()).stream()
                .map(row -> new ContentStatDto(
                        row.getContentType(), row.getTargetId(), row.getTitle(),
                        row.getViews(), roundAvg(row.getAvgDwellMs()), row.getLastViewedAt()))
                .toList();
    }

    /**
     * Daily event and distinct-session counts across the last {@value #TIMESERIES_DAYS} days
     * (oldest first), zero-filled so every day in the window is present.
     */
    @Transactional(readOnly = true)
    public List<TimeseriesPointDto> timeseries() {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(TIMESERIES_DAYS - 1L);
        List<AnalyticsEvent> events =
                analyticsEventRepository.findByOccurredAtBetween(from.atStartOfDay(), endOfDay(to));

        Map<LocalDate, List<AnalyticsEvent>> byDay = events.stream()
                .collect(Collectors.groupingBy(e -> e.getOccurredAt().toLocalDate()));

        Map<LocalDate, TimeseriesPointDto> filled = new LinkedHashMap<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            List<AnalyticsEvent> dayEvents = byDay.getOrDefault(day, List.of());
            long sessions = dayEvents.stream()
                    .map(AnalyticsEvent::getSessionId)
                    .filter(id -> id != null)
                    .distinct()
                    .count();
            filled.put(day, new TimeseriesPointDto(day, dayEvents.size(), sessions));
        }
        return List.copyOf(filled.values());
    }

    /**
     * Categorical breakdown over the last {@value #AGGREGATE_DAYS} days: device mix plus top
     * referrers and paths. Each facet is a grouped query; the top-N lists are limited in SQL.
     */
    @Transactional(readOnly = true)
    public AnalyticsBreakdownDto breakdown() {
        LocalDateTime since = aggregateSince();

        Map<DeviceKind, Long> byDevice = new EnumMap<>(DeviceKind.class);
        for (DeviceKind kind : DeviceKind.values()) {
            byDevice.put(kind, 0L);
        }
        for (AnalyticsEventRepository.DeviceCountRow row : analyticsEventRepository.deviceCountsSince(since)) {
            byDevice.put(row.getDeviceType(), row.getCount());
        }

        List<CountItemDto> topReferrers = analyticsEventRepository
                .topReferrersSince(since, PageRequest.of(0, TOP_REFERRERS)).stream()
                .map(row -> new CountItemDto(row.getLabel(), row.getCount()))
                .toList();
        List<CountItemDto> topPaths = analyticsEventRepository
                .topPathsSince(since, PageRequest.of(0, TOP_PATHS)).stream()
                .map(row -> new CountItemDto(row.getLabel(), row.getCount()))
                .toList();

        return new AnalyticsBreakdownDto(byDevice, topReferrers, topPaths);
    }

    // --- helpers -----------------------------------------------------------

    /** Start of the aggregate look-back window. */
    private LocalDateTime aggregateSince() {
        return LocalDate.now().minusDays(AGGREGATE_DAYS - 1L).atStartOfDay();
    }

    /** Rounds a nullable JPA average (ms) to the nearest long, treating {@code null} as 0. */
    private long roundAvg(Double avg) {
        return avg == null ? 0L : Math.round(avg);
    }

    /** Inclusive end-of-day bound for a date (23:59:59.999999999). */
    private LocalDateTime endOfDay(LocalDate date) {
        return date.atTime(LocalTime.MAX);
    }

    private EventType parseType(String raw) {
        if (raw == null) {
            return EventType.PAGE_VIEW;
        }
        try {
            return EventType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return EventType.PAGE_VIEW;
        }
    }

    private ContentKind parseContentKind(String raw) {
        if (raw == null) {
            return ContentKind.PAGE;
        }
        try {
            return ContentKind.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ContentKind.PAGE;
        }
    }

    private DeviceKind parseDevice(String raw) {
        if (raw == null) {
            return DeviceKind.PC;
        }
        try {
            return DeviceKind.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return DeviceKind.PC;
        }
    }

    private String clamp(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}
