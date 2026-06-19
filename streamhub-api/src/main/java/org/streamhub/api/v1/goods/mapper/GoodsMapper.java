package org.streamhub.api.v1.goods.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.streamhub.api.v1.goods.dto.GoodsDetail;
import org.streamhub.api.v1.goods.dto.GoodsListItem;

/**
 * MyBatis mapper for goods queries (category join + dynamic filters + option-count
 * sub-select). Maps to {@code resources/mappers/GoodsMapper.xml}.
 */
@Mapper
public interface GoodsMapper {

    List<GoodsListItem> selectList(
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId,
            @Param("status") String status,
            @Param("soldOut") String soldOut,
            @Param("orderBy") String orderBy,
            @Param("offset") int offset,
            @Param("size") int size);

    long countList(
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId,
            @Param("status") String status,
            @Param("soldOut") String soldOut);

    /** Base detail (category name). Options and images are loaded by the service. */
    GoodsDetail selectDetail(@Param("id") Long id);
}
