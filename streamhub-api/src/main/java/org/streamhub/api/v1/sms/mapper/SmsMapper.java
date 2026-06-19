package org.streamhub.api.v1.sms.mapper;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.streamhub.api.v1.sms.dto.SmsListItem;

/**
 * MyBatis mapper for SMS history queries (member name join + dynamic filters). Maps to
 * {@code resources/mappers/SmsMapper.xml}.
 */
@Mapper
public interface SmsMapper {

    List<SmsListItem> selectList(
            @Param("keyword") String keyword,
            @Param("kind") String kind,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("orderBy") String orderBy,
            @Param("offset") int offset,
            @Param("size") int size);

    long countList(
            @Param("keyword") String keyword,
            @Param("kind") String kind,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
