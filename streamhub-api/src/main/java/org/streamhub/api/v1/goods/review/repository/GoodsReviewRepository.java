package org.streamhub.api.v1.goods.review.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.streamhub.api.v1.goods.review.entity.GoodsReview;

/** JPA repository for {@link GoodsReview} (goods reviews). */
public interface GoodsReviewRepository extends JpaRepository<GoodsReview, Long> {

    List<GoodsReview> findByDisplayYnOrderByIdDesc(String displayYn);

    List<GoodsReview> findAllByOrderByIdDesc();

    /** A member's own reviews, most recent first (the "내 후기" feed). */
    List<GoodsReview> findByMemberIdOrderByIdDesc(Long memberId);
}
