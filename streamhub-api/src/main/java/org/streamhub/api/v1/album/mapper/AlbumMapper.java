package org.streamhub.api.v1.album.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.streamhub.api.v1.album.dto.AlbumDetail;
import org.streamhub.api.v1.album.dto.AlbumListItem;

/**
 * MyBatis mapper for album queries (dynamic filters + bridge GOODS_ITEM price join).
 * Tracks are loaded separately by the service. Maps to
 * {@code resources/mappers/AlbumMapper.xml}.
 */
@Mapper
public interface AlbumMapper {

    List<AlbumListItem> selectList(
            @Param("keyword") String keyword,
            @Param("genre") String genre,
            @Param("status") String status,
            @Param("orderBy") String orderBy,
            @Param("offset") int offset,
            @Param("size") int size);

    long countList(
            @Param("keyword") String keyword,
            @Param("genre") String genre,
            @Param("status") String status);

    /** Base detail (with bridge GOODS_ITEM price/stock). Tracks are loaded by the service. */
    AlbumDetail selectDetail(@Param("id") Long id);
}
