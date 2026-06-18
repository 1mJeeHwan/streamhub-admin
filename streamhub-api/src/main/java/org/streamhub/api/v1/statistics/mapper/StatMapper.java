package org.streamhub.api.v1.statistics.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.streamhub.api.v1.statistics.dto.ChannelWatchItem;
import org.streamhub.api.v1.statistics.dto.SummaryResponse;
import org.streamhub.api.v1.statistics.dto.TopContentItem;
import org.streamhub.api.v1.statistics.dto.TrendPoint;

/**
 * MyBatis aggregation queries for the dashboard. Maps to
 * {@code resources/mappers/StatMapper.xml}.
 */
@Mapper
public interface StatMapper {

    SummaryResponse summary(@Param("since") LocalDateTime since);

    List<TrendPoint> memberTrend(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    List<TopContentItem> topContents(@Param("limit") int limit);

    List<ChannelWatchItem> watchByChannel(@Param("since") LocalDateTime since);
}
