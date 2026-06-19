package org.streamhub.api.v1.order.mapper;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.streamhub.api.v1.order.dto.OrderDetail;
import org.streamhub.api.v1.order.dto.OrderListItem;

/**
 * MyBatis mapper for order queries (member join + dynamic filters + line-item count).
 * Maps to {@code resources/mappers/OrderMapper.xml}.
 */
@Mapper
public interface OrderMapper {

    List<OrderListItem> selectList(
            @Param("searchField") String searchField,
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("payMethod") String payMethod,
            @Param("churchId") Long churchId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("offset") int offset,
            @Param("size") int size);

    long countList(
            @Param("searchField") String searchField,
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("payMethod") String payMethod,
            @Param("churchId") Long churchId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    /** Base detail (member name). Line items and receipts are loaded by the service. */
    OrderDetail selectDetail(@Param("id") Long id);
}
