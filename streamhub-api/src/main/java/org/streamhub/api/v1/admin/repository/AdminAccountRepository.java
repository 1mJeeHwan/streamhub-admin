package org.streamhub.api.v1.admin.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.streamhub.api.v1.admin.entity.AdminAccount;

/**
 * JPA repository for {@link AdminAccount}. Simple CRUD — complex queries use MyBatis elsewhere.
 */
public interface AdminAccountRepository extends JpaRepository<AdminAccount, Long> {

    Optional<AdminAccount> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);
}
