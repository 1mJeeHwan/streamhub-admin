package org.streamhub.api.v1.content.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.streamhub.api.v1.content.dto.ContentDetail;
import org.streamhub.api.v1.content.dto.ContentListItem;
import org.streamhub.api.v1.content.dto.PublicChannelItem;

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
            @Param("orderBy") String orderBy,
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

    /**
     * Public channel directory: channels owning at least one PUBLISHED content of the given type,
     * with that count, most-active first. Drives the user site's channel-browse carousel.
     *
     * @param type  content type filter ({@code VIDEO}/{@code SOUND}); null spans both
     * @param limit max channels returned
     */
    List<PublicChannelItem> selectPublicChannels(
            @Param("type") String type,
            @Param("limit") int limit);
}
