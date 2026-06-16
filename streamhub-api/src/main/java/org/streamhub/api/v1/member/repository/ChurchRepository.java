package org.streamhub.api.v1.member.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.streamhub.api.v1.member.entity.Church;

/** JPA repository for {@link Church}. */
public interface ChurchRepository extends JpaRepository<Church, Long> {
}
