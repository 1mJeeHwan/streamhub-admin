package org.streamhub.api.v1.content.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.streamhub.api.v1.content.dto.ContentDetail;
import org.streamhub.api.v1.content.dto.ContentListItem;

/**
 * MyBatis mapper for content queries (joins + dynamic filters + hashtag aggregation).
 * Maps to {@code resources/mappers/ContentMapper.xml}.
 */
@Mapper
public interface ContentMapper {

    List<ContentListItem> selectList(
            @Param("keyword") String keyword,
            @Param("type") String type,
            @Param("status") String status,
            @Param("channelId") Long channelId,
            @Param("churchId") Long churchId,
            @Param("offset") int offset,
            @Param("size") int size);

    long countList(
            @Param("keyword") String keyword,
            @Param("type") String type,
            @Param("status") String status,
            @Param("channelId") Long channelId,
            @Param("churchId") Long churchId);

    /** Base detail (channel/church names). Hashtags and files are loaded by the service. */
    ContentDetail selectDetail(@Param("id") Long id);
}
