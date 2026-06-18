package org.streamhub.api.v1.statistics;

import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.streamhub.api.v1.statistics.dto.ChannelWatchItem;
import org.streamhub.api.v1.statistics.dto.SummaryResponse;
import org.streamhub.api.v1.statistics.dto.TopContentItem;
import org.streamhub.api.v1.statistics.dto.TrendPoint;
import org.streamhub.api.v1.statistics.mapper.StatMapper;

/**
 * Dashboard statistics via MyBatis aggregation. The summary is cached in Redis
 * (60s TTL) since it is the most-hit, recompute-heavy query.
 */
@Slf4j
@Service
public class StatService {

    /** Look-back window (days) for the channel watch-time aggregation. */
    private static final int WATCH_WINDOW_DAYS = 90;

    private final StatMapper statMapper;

    public StatService(StatMapper statMapper) {
        this.statMapper = statMapper;
    }

    /** Summary cards. Cached under {@code summary::all}; recomputed on miss / after TTL. */
    @Cacheable(cacheNames = "summary", key = "'all'")
    public SummaryResponse getSummary() {
        log.info("Computing dashboard summary (cache miss)");
        return statMapper.summary(LocalDateTime.now().minusDays(7));
    }

    public List<TrendPoint> getMemberTrend(int days) {
        int range = days <= 0 ? 30 : days;
        LocalDateTime to = LocalDateTime.now();
        return statMapper.memberTrend(to.minusDays(range), to);
    }

    public List<TopContentItem> getTopContents(int limit) {
        return statMapper.topContents(limit <= 0 ? 5 : limit);
    }

    /**
     * Watch time per channel bucket, aggregated from the live analytics pipeline
     * (CONTENT_VIEW dwell time in {@code ANALYTICS_EVENT}) rather than the dead
     * {@code WATCH_HISTORY} table. Returns the same {@link ChannelWatchItem} shape
     * ({@code channelName}, {@code totalSeconds}) the dashboard chart already consumes,
     * so the frontend is unchanged. Looks back {@value #WATCH_WINDOW_DAYS} days.
     */
    public List<ChannelWatchItem> getWatchByChannel() {
        return statMapper.watchByChannel(LocalDateTime.now().minusDays(WATCH_WINDOW_DAYS));
    }
}
