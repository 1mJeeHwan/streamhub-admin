package org.streamhub.api.v1.worship.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.streamhub.api.v1.worship.entity.WorshipRegistration;

/** JPA repository for {@link WorshipRegistration} (CRUD). Listing/search uses MyBatis. */
public interface WorshipRegistrationRepository extends JpaRepository<WorshipRegistration, Long> {

    boolean existsByRegNo(String regNo);

    /** Number of worship-service registrations for a church — drives the church-detail count. */
    long countByChurchId(Long churchId);
}
