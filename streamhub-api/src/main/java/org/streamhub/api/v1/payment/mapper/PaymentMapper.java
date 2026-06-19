package org.streamhub.api.v1.payment.mapper;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.streamhub.api.v1.payment.dto.PaymentListItem;

/**
 * MyBatis mapper for payment-history queries: {@code ORDER_RECEIPT} joined with its order and
 * the paying member, with dynamic kind/method/provider/date filters.
 * Maps to {@code resources/mappers/PaymentMapper.xml}.
 */
@Mapper
public interface PaymentMapper {

    List<PaymentListItem> selectList(
            @Param("searchField") String searchField,
            @Param("keyword") String keyword,
            @Param("kind") String kind,
            @Param("method") String method,
            @Param("provider") String provider,
            @Param("churchId") Long churchId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("orderBy") String orderBy,
            @Param("offset") int offset,
            @Param("size") int size);

    long countList(
            @Param("searchField") String searchField,
            @Param("keyword") String keyword,
            @Param("kind") String kind,
            @Param("method") String method,
            @Param("provider") String provider,
            @Param("churchId") Long churchId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);
}
