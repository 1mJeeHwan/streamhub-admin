package org.streamhub.api.v1.member.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.streamhub.api.v1.member.dto.PointLedgerListItem;

/**
 * MyBatis mapper for point-ledger queries that join the owning member — the joined,
 * filtered listing the JPA repository handles awkwardly. Maps to
 * {@code resources/mappers/PointMapper.xml}.
 */
@Mapper
public interface PointMapper {

    /**
     * Paginated, filtered ledger list joined with member name/email/church.
     *
     * @param keyword  member name/email or ledger reason LIKE filter (nullable)
     * @param memberId restrict to a single member (nullable)
     * @param churchId church filter (nullable; set by RBAC scope or user filter)
     * @param orderBy  safe {@code ORDER BY} body resolved by the service (never raw client input)
     * @param offset   row offset
     * @param size     page size
     */
    List<PointLedgerListItem> selectList(
            @Param("keyword") String keyword,
            @Param("memberId") Long memberId,
            @Param("churchId") Long churchId,
            @Param("orderBy") String orderBy,
            @Param("offset") int offset,
            @Param("size") int size);

    /** Total rows matching the same filters as {@link #selectList}. */
    long countList(
            @Param("keyword") String keyword,
            @Param("memberId") Long memberId,
            @Param("churchId") Long churchId);

    /** Single ledger row with member name/email/church (the just-created entry). */
    PointLedgerListItem selectById(@Param("id") Long id);
}
