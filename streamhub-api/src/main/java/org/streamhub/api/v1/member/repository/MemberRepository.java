package org.streamhub.api.v1.member.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
