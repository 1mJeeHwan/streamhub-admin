package org.streamhub.api.v1.member.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.streamhub.api.v1.member.entity.Region;

/** JPA repository for {@link Region}. */
public interface RegionRepository extends JpaRepository<Region, Long> {
}
