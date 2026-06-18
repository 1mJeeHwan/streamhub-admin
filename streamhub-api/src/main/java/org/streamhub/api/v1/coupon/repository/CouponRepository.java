package org.streamhub.api.v1.coupon.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.streamhub.api.v1.coupon.entity.Coupon;

/** JPA repository for {@link Coupon} (discount coupons). */
public interface CouponRepository extends JpaRepository<Coupon, Long> {

    boolean existsByCode(String code);

    /** Looks up a coupon by its unique code (redemption path). */
    Optional<Coupon> findByCode(String code);
}
