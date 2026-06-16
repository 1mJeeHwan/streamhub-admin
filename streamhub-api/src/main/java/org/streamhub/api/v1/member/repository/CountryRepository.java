package org.streamhub.api.v1.member.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.streamhub.api.v1.member.entity.Country;

/** JPA repository for {@link Country}. */
public interface CountryRepository extends JpaRepository<Country, Long> {
}
