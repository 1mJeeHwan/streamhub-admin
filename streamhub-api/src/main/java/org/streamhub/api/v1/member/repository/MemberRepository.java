package org.streamhub.api.v1.member.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.streamhub.api.v1.member.entity.Member;

/**
 * JPA repository for {@link Member}. Simple CRUD and id-set lookups; the paginated
 * search lives in MyBatis ({@code MemberMapper}).
 */
public interface MemberRepository extends JpaRepository<Member, Long> {

    List<Member> findAllByIdIn(List<Long> ids);

    long countByChurchId(Long churchId);

    /** Member login is by email. */
    Optional<Member> findByEmail(String email);

    /**
     * Atomically adjusts the cached point balance by {@code delta} in a single guarded UPDATE,
     * eliminating the read-modify-write race that let concurrent grant/deduct/accrual writers
     * lose each other's updates (mirrors {@code GoodsItemRepository.decrementStock} and
     * {@code CouponRepository.incrementUsedCount}). The {@code balance + delta >= 0} guard means
     * a deduction that would underflow simply affects 0 rows (caller treats that as
     * insufficient-balance) instead of driving the balance negative. The persistence context is
     * cleared automatically so a subsequent re-read returns the freshly persisted balance.
     *
     * @param id    the member id
     * @param delta signed point change (negative deducts)
     * @return rows affected — {@code 1} on success, {@code 0} if the member is missing or the
     *         deduction would drive the balance below zero
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Member m SET m.pointBalance = m.pointBalance + :delta, m.updatedAt = CURRENT_TIMESTAMP "
            + "WHERE m.id = :id AND m.pointBalance + :delta >= 0")
    int adjustBalance(@Param("id") Long id, @Param("delta") long delta);
}
