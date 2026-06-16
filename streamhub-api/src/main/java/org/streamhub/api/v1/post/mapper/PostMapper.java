package org.streamhub.api.v1.post.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.streamhub.api.v1.post.dto.PostListItem;

/**
 * MyBatis mapper for post list queries (keyword search + status filter + pagination).
 * Maps to {@code resources/mappers/PostMapper.xml}. Detail is read via JPA.
 */
@Mapper
public interface PostMapper {

    List<PostListItem> selectList(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("offset") int offset,
            @Param("size") int size);

    long countList(
            @Param("keyword") String keyword,
            @Param("status") String status);
}
