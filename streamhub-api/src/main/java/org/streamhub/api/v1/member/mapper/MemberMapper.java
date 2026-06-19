package org.streamhub.api.v1.member.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.streamhub.api.v1.member.dto.MemberDetail;
import org.streamhub.api.v1.member.dto.MemberListItem;

/**
 * MyBatis mapper for member queries that need joins and dynamic filtering —
 * the kind of read the JPA repository handles awkwardly. Maps to
 * {@code resources/mappers/MemberMapper.xml}.
 */
@Mapper
public interface MemberMapper {

    /**
     * Paginated, filtered member list joined with church/region/country names.
     *
     * @param keyword    name/email/phone LIKE filter (nullable)
     * @param userStatus {@code UserStatus} name filter (nullable)
     * @param churchId   church filter (nullable; set by RBAC scope or user filter)
     * @param orderBy    safe {@code ORDER BY} body built by {@code SortResolver} (whitelisted columns only)
     * @param offset     row offset
     * @param size       page size
     */
    List<MemberListItem> selectList(
            @Param("keyword") String keyword,
            @Param("userStatus") String userStatus,
            @Param("churchId") Long churchId,
            @Param("orderBy") String orderBy,
            @Param("offset") int offset,
            @Param("size") int size);

    /** Total rows matching the same filters as {@link #selectList}. */
    long countList(
            @Param("keyword") String keyword,
            @Param("userStatus") String userStatus,
            @Param("churchId") Long churchId);

    /** Single member detail with church/region/country names. */
    MemberDetail selectDetail(@Param("id") Long id);
}
