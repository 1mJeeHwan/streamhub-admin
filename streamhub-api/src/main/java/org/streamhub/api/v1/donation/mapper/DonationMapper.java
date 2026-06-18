package org.streamhub.api.v1.donation.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.streamhub.api.v1.donation.dto.BillingCalendarItem;
import org.streamhub.api.v1.donation.dto.DonationListItem;

/**
 * MyBatis mapper for donation queries (joins + dynamic filters + calendar aggregation).
 * Maps to {@code resources/mappers/DonationMapper.xml}.
 */
@Mapper
public interface DonationMapper {

    List<DonationListItem> selectList(
            @Param("keyword") String keyword,
            @Param("type") String type,
            @Param("status") String status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("churchId") Long churchId,
            @Param("offset") int offset,
            @Param("size") int size);

    long countList(
            @Param("keyword") String keyword,
            @Param("type") String type,
            @Param("status") String status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("churchId") Long churchId);

    DonationListItem selectDetail(@Param("id") Long id);

    /** Active subscriptions' next-billing forecast, grouped by day within the month range. */
    List<BillingCalendarItem> billingCalendar(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("churchId") Long churchId);
}
