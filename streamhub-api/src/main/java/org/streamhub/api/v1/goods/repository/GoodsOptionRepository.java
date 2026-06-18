package org.streamhub.api.v1.goods.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.streamhub.api.v1.goods.entity.GoodsOption;

/** JPA repository for {@link GoodsOption} (per-item option rows, replace-on-save). */
public interface GoodsOptionRepository extends JpaRepository<GoodsOption, Long> {

    List<GoodsOption> findByItemId(Long itemId);

    List<GoodsOption> findByItemIdOrderBySortAscIdAsc(Long itemId);

    void deleteByItemId(Long itemId);

    /**
     * Atomically deducts option stock under a {@code stock >= qty} guard, mirroring
     * {@link GoodsItemRepository#decrementStock} so option-level checkouts cannot oversell.
     *
     * @return rows affected — {@code 1} on success, {@code 0} if stock was insufficient
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE GoodsOption o SET o.stock = o.stock - :qty WHERE o.id = :id AND o.stock >= :qty")
    int decrementStock(@Param("id") Long id, @Param("qty") int qty);

    /**
     * Atomically restores option stock on cancel/return; no lower-bound guard is needed.
     *
     * @return rows affected — {@code 1} if the option exists, otherwise {@code 0}
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE GoodsOption o SET o.stock = o.stock + :qty WHERE o.id = :id")
    int restoreStock(@Param("id") Long id, @Param("qty") int qty);
}
