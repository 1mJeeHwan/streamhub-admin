package org.streamhub.api.v1.worship.mapper;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.streamhub.api.v1.worship.dto.WorshipRegistrationDetail;
import org.streamhub.api.v1.worship.dto.WorshipRegistrationListItem;

/**
 * MyBatis mapper for worship registration queries (church join + dynamic filters +
 * family-count subquery). Maps to {@code resources/mappers/WorshipMapper.xml}.
 */
@Mapper
public interface WorshipMapper {

    List<WorshipRegistrationListItem> selectList(
            @Param("searchField") String searchField,
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("churchId") Long churchId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("orderBy") String orderBy,
            @Param("offset") int offset,
            @Param("size") int size);

    long countList(
            @Param("searchField") String searchField,
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("churchId") Long churchId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    /** Base detail (church name). Family rows are loaded by the service. */
    WorshipRegistrationDetail selectDetail(@Param("id") Long id);
}
