package org.streamhub.api.v1.dashboard;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.streamhub.api.base.security.AdminPrincipal;
import org.streamhub.api.v1.dashboard.dto.DashboardSummaryResponse;
import org.streamhub.api.v1.dashboard.dto.FeedItem;
import org.streamhub.api.v1.dashboard.dto.FeedRow;
import org.streamhub.api.v1.dashboard.dto.KpiDelta;
import org.streamhub.api.v1.dashboard.dto.TimeseriesResponse;
import org.streamhub.api.v1.dashboard.dto.TrendRow;
import org.streamhub.api.v1.dashboard.mapper.DashboardMapper;

/**
 * Aggregation orchestration for the operations dashboard. Composes per-KPI aggregate
 * queries into the dashboard responses. The two heaviest, most-hit endpoints
 * ({@code summary}, {@code timeseries}) are cached in Redis (60s TTL, the default in
 * {@code CacheConfig}); the activity feed is never cached so it always reflects the
 * latest events.
 */
@Slf4j
@Service
public class DashboardService {

    /** Default timeseries window when an invalid {@code days} is requested. */
    private static final int DEFAULT_TIMESERIES_DAYS = 90;

    /** Default activity-feed page size when an invalid {@code limit} is requested. */
    private static final int DEFAULT_FEED_LIMIT = 20;

    private final DashboardMapper dashboardMapper;

    public DashboardService(DashboardMapper dashboardMapper) {
        this.dashboardMapper = dashboardMapper;
    }

    /**
     * Builds the 6-KPI strip. Cached per church under {@code dashboardSummary} (key is the
     * operator's churchId, {@code "all"} for SYSTEM) so a CHURCH_MANAGER never sees another
     * church's cached figures; recomputed on miss / after TTL. "Today" vs "yesterday" windows
     * drive the ▲▼ comparison for the revenue KPI; structural counts (open orders, low stock,
     * active subscribers) have no natural previous-period so their delta is 0.
     *
     * @param principal authenticated operator; CHURCH_MANAGER scopes every member-linked KPI
     * @return the populated summary
     */
    @Cacheable(cacheNames = "dashboardSummary",
            key = "#principal.isSystem() ? 'all' : #principal.churchId()")
    public DashboardSummaryResponse getSummary(AdminPrincipal principal) {
        log.info("Computing dashboard summary (cache miss)");

        Long churchId = scopedChurchId(principal);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime yesterdayStart = todayStart.minusDays(1);

        long revenueToday = dashboardMapper.sumOrderRevenue(todayStart, now, churchId)
                + dashboardMapper.sumDonationAmount(todayStart, now, churchId);
        long revenueYesterday = dashboardMapper.sumOrderRevenue(yesterdayStart, todayStart, churchId)
                + dashboardMapper.sumDonationAmount(yesterdayStart, todayStart, churchId);

        long newSubsToday = dashboardMapper.countNewSubscriptions(todayStart, now, churchId);
        long newSubsYesterday = dashboardMapper.countNewSubscriptions(yesterdayStart, todayStart, churchId);

        long openOrders = dashboardMapper.countOpenOrders(churchId);
        // Inventory is global (GOODS_ITEM has no church linkage); same low-stock count for everyone.
        long lowStock = dashboardMapper.countLowStock();
        long activeSubscribers = dashboardMapper.countActiveSubscribers(churchId);

        // No INQUIRY table exists yet — unanswered inquiries are always 0 until the
        // inquiry domain lands, at which point this becomes a mapper count query.
        long unansweredInquiry = 0L;

        DashboardSummaryResponse response = new DashboardSummaryResponse();
        response.setTodayRevenue(KpiDelta.of(revenueToday, revenueYesterday, sevenDayRevenueSpark(now, churchId)));
        response.setNewSubscriptions(KpiDelta.of(newSubsToday, newSubsYesterday, null));
        response.setOpenOrders(KpiDelta.of(openOrders, openOrders, null));
        response.setUnansweredInquiry(KpiDelta.of(unansweredInquiry, unansweredInquiry, null));
        response.setLowStock(KpiDelta.of(lowStock, lowStock, null));
        response.setActiveSubscribers(KpiDelta.of(activeSubscribers, activeSubscribers, null));
        return response;
    }

    /**
     * Builds the stacked timeseries over the trailing {@code days}. Every day in the
     * range is emitted (sparse aggregation rows are densified to zeros) so the chart
     * axis is continuous. Cached per {@code days} and per church (60s) so a CHURCH_MANAGER
     * never sees another church's cached series.
     *
     * @param days      window length; non-positive falls back to {@value #DEFAULT_TIMESERIES_DAYS}
     * @param principal authenticated operator; CHURCH_MANAGER scopes the aggregation
     * @return the densified timeseries
     */
    @Cacheable(cacheNames = "dashboardTimeseries",
            key = "#days + '-' + (#principal.isSystem() ? 'all' : #principal.churchId())")
    public TimeseriesResponse getTimeseries(int days, AdminPrincipal principal) {
        log.info("Computing dashboard timeseries for {} days (cache miss)", days);

        Long churchId = scopedChurchId(principal);
        int range = days <= 0 ? DEFAULT_TIMESERIES_DAYS : days;
        LocalDateTime to = LocalDateTime.now();
        LocalDate fromDate = to.toLocalDate().minusDays(range - 1L);
        LocalDateTime from = fromDate.atStartOfDay();

        List<TrendRow> rows = dashboardMapper.dailyTrend(from, to, churchId);
        Map<String, TrendRow> byDate = new HashMap<>();
        for (TrendRow row : rows) {
            byDate.put(row.getDate(), row);
        }

        TimeseriesResponse response = new TimeseriesResponse();
        for (LocalDate day = fromDate; !day.isAfter(to.toLocalDate()); day = day.plusDays(1)) {
            String key = day.toString();
            TrendRow row = byDate.get(key);
            response.getCategories().add(key);
            response.getGoodsRevenue().add(row != null ? row.getGoodsRevenue() : 0L);
            response.getRecurringDonation().add(row != null ? row.getRecurringDonation() : 0L);
            response.getOnceDonation().add(row != null ? row.getOnceDonation() : 0L);
        }
        return response;
    }

    /**
     * Builds the activity feed: the most recent {@code limit} events across orders,
     * subscriptions and donations, newest first. Never cached — always the latest.
     *
     * @param limit     max items; non-positive falls back to {@value #DEFAULT_FEED_LIMIT}
     * @param principal authenticated operator; CHURCH_MANAGER sees only their church's events
     * @return presentation-ready feed items
     */
    public List<FeedItem> getFeed(int limit, AdminPrincipal principal) {
        int size = limit <= 0 ? DEFAULT_FEED_LIMIT : limit;
        List<FeedRow> rows = dashboardMapper.recentActivity(size, scopedChurchId(principal));

        List<FeedItem> items = new ArrayList<>(rows.size());
        for (FeedRow row : rows) {
            FeedItem item = new FeedItem();
            item.setId(row.getKind() + "-" + row.getSourceId());
            item.setKind(row.getKind());
            item.setActorName(maskName(row.getMemberName()));
            item.setOccurredAt(row.getOccurredAt());
            item.setMessage(humanize(row, item.getActorName()));
            items.add(item);
        }
        return items;
    }

    /** Daily total revenue for the trailing 7 days, oldest→newest, for the KPI sparkline. */
    private List<Long> sevenDayRevenueSpark(LocalDateTime now, Long churchId) {
        LocalDate today = now.toLocalDate();
        List<Long> spark = new ArrayList<>(7);
        for (int i = 6; i >= 0; i--) {
            LocalDateTime dayStart = today.minusDays(i).atStartOfDay();
            LocalDateTime dayEnd = dayStart.plusDays(1);
            spark.add(dashboardMapper.sumOrderRevenue(dayStart, dayEnd, churchId)
                    + dashboardMapper.sumDonationAmount(dayStart, dayEnd, churchId));
        }
        return spark;
    }

    /** Resolves the church filter: CHURCH_MANAGER is pinned to its own church, SYSTEM sees all. */
    private Long scopedChurchId(AdminPrincipal principal) {
        return principal.isSystem() ? null : principal.churchId();
    }

    /** Builds a completed Korean sentence for one feed row. */
    private String humanize(FeedRow row, String actorName) {
        switch (row.getKind()) {
            case "ORDER":
                return actorName + "님이 주문 " + orderStatusLabel(row.getStatus())
                        + "(" + formatKrw(row.getAmount()) + ")";
            case "SUBSCRIPTION":
                return actorName + "님이 정기후원 시작";
            case "DONATION":
                String typeLabel = "SUBSCRIPTION".equals(row.getStatus()) ? "정기후원 결제" : "단건후원";
                return actorName + "님이 " + typeLabel + "(" + formatKrw(row.getAmount()) + ")";
            default:
                return actorName + "님의 활동";
        }
    }

    /** Maps an order status token to a short Korean label. */
    private String orderStatusLabel(String status) {
        if (status == null) {
            return "처리";
        }
        switch (status) {
            case "PLACED":
                return "접수";
            case "PAID":
                return "결제완료";
            case "READY":
                return "배송준비";
            case "SHIPPING":
                return "배송중";
            case "DONE":
                return "완료";
            case "CANCEL":
                return "취소";
            case "RETURN":
                return "반품";
            default:
                return "처리";
        }
    }

    /** Formats a KRW amount as e.g. {@code "9,900원"}; {@code null} → {@code "-"}. */
    private String formatKrw(Long amount) {
        if (amount == null) {
            return "-";
        }
        return String.format("%,d원", amount);
    }

    /** Masks a member name middle character, e.g. {@code "김지환"} → {@code "김O환"}. */
    private String maskName(String name) {
        if (name == null || name.isBlank()) {
            return "시스템";
        }
        if (name.length() <= 2) {
            return name.charAt(0) + "O";
        }
        return name.charAt(0) + "O" + name.substring(name.length() - 1);
    }
}
