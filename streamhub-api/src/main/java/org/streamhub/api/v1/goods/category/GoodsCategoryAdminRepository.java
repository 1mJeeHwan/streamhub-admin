package org.streamhub.api.v1.goods.category;

import org.springframework.data.jpa.repository.JpaRepository;
import org.streamhub.api.v1.goods.entity.GoodsCategory;

/**
 * Admin-side JPA repository for {@link GoodsCategory}. Defined separately from
 * {@code GoodsCategoryRepository} so the category admin module owns its own data access
 * without touching the existing goods repositories.
 */
public interface GoodsCategoryAdminRepository extends JpaRepository<GoodsCategory, Long> {

    /** Number of direct child categories under {@code parentId}; used by the delete guard. */
    long countByParentId(Long parentId);
}
