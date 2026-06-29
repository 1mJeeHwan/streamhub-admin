package org.streamhub.api.v1.visit;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.streamhub.api.v1.visit.dto.DailyCountDto;
import org.streamhub.api.v1.visit.dto.PathCountDto;
import org.streamhub.api.v1.visit.dto.VisitLogDto;
import org.streamhub.api.v1.visit.dto.VisitSearchRequest;
import org.streamhub.api.v1.visit.dto.VisitSummaryDto;
import org.streamhub.api.v1.visit.entity.DeviceType;
import org.streamhub.api.v1.visit.entity.VisitLog;
import org.streamhub.api.v1.visit.repository.VisitLogRepository;

/**
 * Front-site visit statistics (접속 통계). The demo dataset is small (~400 rows), so every
 * aggregate is computed in memory from a single range scan rather than via grouped SQL — no
 * analytics store or window functions needed.
 */
@Slf4j
@Service
public class VisitService {

    /** Default look-back window when the request supplies no bounds. */
    private static final int DEFAULT_WINDOW_DAYS = 30;

    /** Cap on rows returned by the list endpoint. */
    private static final int LIST_LIMIT = 500;

    /** Number of top paths returned in the summary. */
    private static final int TOP_PATHS = 5;

    private final VisitLogRepository visitLogRepository;

    public VisitService(VisitLogRepository visitLogRepository) {
        this.visitLogRepository = visitLogRepository;
    }

    /**
     * Records one real site visit (접속 통계), keyed by the client's IP masked to its first two
     * octets ({@code "211.45.*.*"}). Called from the public analytics ingest on every PAGE_VIEW, so
     * the visit stat reflects who actually accesses the site — not just the seeded demo rows.
     * Best-effort: a failure here never breaks the page-view ingest.
     * ponytail: one row per page view; add a per-session+path cooldown if volume ever matters.
     */
    @Transactional
    public void record(String clientIp, String userAgent, String path, String deviceType, Long memberId) {
        try {
            LocalDateTime now = LocalDateTime.now();
            visitLogRepository.save(VisitLog.builder()
                    .visitedAt(now)
                    .createdAt(now)
                    .ipMasked(maskIp(clientIp))
                    .userAgent(clamp(userAgent, 300))
                    .deviceType(deviceFrom(deviceType, userAgent))
                    .path(clamp(path, 200))
                    .memberId(memberId)
                    .build());
        } catch (RuntimeException ex) {
            log.warn("visit record skipped: {}", ex.getMessage());
        }
    }

    /** Masks an IP to its first two octets (IPv4) or first two hextets (IPv6); null/blank → null. */
    static String maskIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        String s = ip.trim();
        if (s.indexOf('.') > 0) {
            String[] p = s.split("\\.");
            return p.length == 4 ? p[0] + "." + p[1] + ".*.*" : s;
        }
        if (s.contains(":")) {
            String[] p = s.split(":");
            return p.length >= 2 ? p[0] + ":" + p[1] + ":*" : s;
        }
        return s;
    }

    private DeviceType deviceFrom(String deviceType, String userAgent) {
        String dt = deviceType == null ? "" : deviceType.toUpperCase(java.util.Locale.ROOT);
        if (dt.contains("MOBILE")) {
            return DeviceType.MOBILE;
        }
        if (dt.contains("PC") || dt.contains("DESKTOP")) {
            return DeviceType.PC;
        }
        return userAgent != null && userAgent.toLowerCase(java.util.Locale.ROOT).contains("mobi")
                ? DeviceType.MOBILE : DeviceType.PC;
    }

    private String clamp(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    /** Visit list within the requested period (newest first), capped to {@value #LIST_LIMIT} rows. */
    @Transactional(readOnly = true)
    public List<VisitLogDto> list(VisitSearchRequest request) {
        LocalDate to = request != null && request.toDate() != null ? request.toDate() : LocalDate.now();
        LocalDate from = request != null && request.fromDate() != null
                ? request.fromDate() : to.minusDays(DEFAULT_WINDOW_DAYS);
        DeviceType deviceType = request != null ? request.deviceType() : null;
        String keyword = request != null && request.keyword() != null
                ? request.keyword().trim().toLowerCase() : null;

        return visitLogRepository.findByVisitedAtBetween(from.atStartOfDay(), endOfDay(to)).stream()
                .filter(log -> deviceType == null || deviceType == log.getDeviceType())
                .filter(log -> matchesKeyword(log, keyword))
                .sorted(Comparator.comparing(VisitLog::getVisitedAt).reversed())
                .limit(LIST_LIMIT)
                .map(VisitLogDto::from)
                .toList();
    }

    /**
     * Daily visit counts across the requested range (oldest first), with zero-fill so every day in
     * the window is present. Falls back to the last {@value #DEFAULT_WINDOW_DAYS} days when no range
     * is given.
     */
    @Transactional(readOnly = true)
    public List<DailyCountDto> daily(VisitSearchRequest request) {
        LocalDate to = request != null && request.toDate() != null ? request.toDate() : LocalDate.now();
        LocalDate from = request != null && request.fromDate() != null
                ? request.fromDate() : to.minusDays(DEFAULT_WINDOW_DAYS - 1L);

        Map<LocalDate, Long> counts = visitLogRepository
                .findByVisitedAtBetween(from.atStartOfDay(), endOfDay(to)).stream()
                .collect(Collectors.groupingBy(
                        log -> log.getVisitedAt().toLocalDate(), Collectors.counting()));

        Map<LocalDate, Long> filled = new LinkedHashMap<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            filled.put(day, counts.getOrDefault(day, 0L));
        }
        return filled.entrySet().stream()
                .map(entry -> new DailyCountDto(entry.getKey(), entry.getValue()))
                .toList();
    }

    /** All-time traffic summary: totals, today, approx unique IPs, top paths and device breakdown. */
    @Transactional(readOnly = true)
    public VisitSummaryDto summary() {
        List<VisitLog> logs = visitLogRepository.findAll();
        LocalDate today = LocalDate.now();

        long todayVisits = logs.stream()
                .filter(log -> today.equals(log.getVisitedAt().toLocalDate()))
                .count();
        long uniqueIpApprox = logs.stream()
                .map(VisitLog::getIpMasked)
                .filter(ip -> ip != null)
                .distinct()
                .count();

        List<PathCountDto> topPaths = logs.stream()
                .collect(Collectors.groupingBy(VisitLog::getPath, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(TOP_PATHS)
                .map(entry -> new PathCountDto(entry.getKey(), entry.getValue()))
                .toList();

        Map<DeviceType, Long> deviceBreakdown = new EnumMap<>(DeviceType.class);
        for (DeviceType type : DeviceType.values()) {
            deviceBreakdown.put(type, 0L);
        }
        for (VisitLog log : logs) {
            if (log.getDeviceType() != null) {
                deviceBreakdown.merge(log.getDeviceType(), 1L, Long::sum);
            }
        }

        return new VisitSummaryDto(logs.size(), todayVisits, uniqueIpApprox, topPaths, deviceBreakdown);
    }

    // --- helpers -----------------------------------------------------------

    /** Inclusive end-of-day bound for a date (23:59:59.999999999). */
    private LocalDateTime endOfDay(LocalDate date) {
        return date.atTime(LocalTime.MAX);
    }

    private boolean matchesKeyword(VisitLog log, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        return containsIgnoreCase(log.getPath(), keyword)
                || containsIgnoreCase(log.getBrowser(), keyword)
                || containsIgnoreCase(log.getOs(), keyword);
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }
}
