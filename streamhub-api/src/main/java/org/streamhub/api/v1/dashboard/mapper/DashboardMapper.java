package org.streamhub.api.v1.dashboard.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.streamhub.api.v1.dashboard.dto.FeedRow;
import org.streamhub.api.v1.dashboard.dto.TrendRow;

/**
 * MyBatis aggregation queries for the operations dashboard. Maps to
 * {@code resources/mappers/DashboardMapper.xml}. Read-only — every method is a SELECT
 * over the commerce/donation tables ({@code ORDERS}, {@code DONATION},
 * {@code SUBSCRIPTION}, {@code GOODS_ITEM}, {@code MEMBER}). N+1-free: each KPI is a
 * single aggregate query.
 */
@Mapper
public interface DashboardMapper {

    /**
     * Settled order revenue ({@code total} of DONE/SHIPPING/READY/PAID orders) in a window.
     * {@code churchId} restricts to one church via the {@code MEMBER} join (null = all churches).
     */
    long sumOrderRevenue(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                         @Param("churchId") Long churchId);

    /** Sum of successfully PAID donations in a window, optionally scoped to one church. */
    long sumDonationAmount(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                           @Param("churchId") Long churchId);

    /** Subscriptions whose {@code started_at} falls in a window, optionally scoped to one church. */
    long countNewSubscriptions(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                               @Param("churchId") Long churchId);

    /** Orders in a non-terminal state (PLACED/PAID/READY/SHIPPING), optionally scoped to one church. */
    long countOpenOrders(@Param("churchId") Long churchId);

    /** Goods items at or below their low-stock threshold and still on sale (global inventory). */
    long countLowStock();

    /** Currently ACTIVE recurring-donation subscriptions, optionally scoped to one church. */
    long countActiveSubscribers(@Param("churchId") Long churchId);

    /** Daily goods-revenue / recurring / once-donation totals across a date range (sparse). */
    List<TrendRow> dailyTrend(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                              @Param("churchId") Long churchId);

    /** Most recent N activity events (orders ∪ subscriptions ∪ donations), newest first. */
    List<FeedRow> recentActivity(@Param("limit") int limit, @Param("churchId") Long churchId);
}
