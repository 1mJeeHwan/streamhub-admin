package org.streamhub.api.v1.donation.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.streamhub.api.v1.donation.dto.SubscriptionDetail;
import org.streamhub.api.v1.donation.dto.SubscriptionListItem;

/**
 * MyBatis mapper for subscription queries (member/plan joins + dynamic filters).
 * Maps to {@code resources/mappers/SubscriptionMapper.xml}.
 */
@Mapper
public interface SubscriptionMapper {

    List<SubscriptionListItem> selectList(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("planId") Long planId,
            @Param("churchId") Long churchId,
            @Param("orderBy") String orderBy,
            @Param("offset") int offset,
            @Param("size") int size);

    long countList(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("planId") Long planId,
            @Param("churchId") Long churchId);

    SubscriptionDetail selectDetail(@Param("id") Long id);
}
