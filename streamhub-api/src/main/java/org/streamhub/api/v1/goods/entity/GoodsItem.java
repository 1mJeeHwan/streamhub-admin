package org.streamhub.api.v1.goods.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A goods (merchandise) item sold in the church shop. */
@Entity
@Table(name = "GOODS_ITEM", indexes = {
        @Index(name = "idx_goods_item_category", columnList = "category_id"),
        @Index(name = "idx_goods_item_status", columnList = "status"),
        @Index(name = "idx_goods_item_created", columnList = "created_at"),
        @Index(name = "idx_goods_item_code", columnList = "code")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoodsItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Optimistic-lock version guarding concurrent admin stock edits. Primitive {@code long}
     * (not {@code Long}) so rows created before this column existed — back-filled as NULL by
     * {@code ddl-auto=update} — are read by Hibernate as {@code 0} rather than tripping a
     * spurious lock failure on first update.
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /** FK → GOODS_CATEGORY. */
    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /** Product code, e.g. {@code GD0001}. */
    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "description", length = 2000)
    private String description;

    /** Selling price (KRW). */
    @Column(name = "price", nullable = false)
    private Long price;

    /** Market/list price (KRW). */
    @Column(name = "list_price")
    private Long listPrice;

    /** Warehouse stock (baseline stock when the item has no options). */
    @Column(name = "stock", nullable = false)
    private Integer stock;

    /** Low-stock notification threshold. */
    @Column(name = "noti_qty", nullable = false)
    private Integer notiQty;

    /** "Y"/"N" — sold out. */
    @Column(name = "sold_out", nullable = false, length = 1)
    private String soldOut;

    /** "Y"/"N" — on sale. */
    @Column(name = "use_yn", nullable = false, length = 1)
    private String useYn;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private GoodsStatus status;

    @Column(name = "sale_count", nullable = false)
    private Integer saleCount;

    @Column(name = "view_count", nullable = false)
    private Long viewCount;

    @Column(name = "thumbnail_key", length = 300)
    private String thumbnailKey;

    /** Comma-joined display badges (e.g. {@code HIT,NEW,SALE}). */
    @Column(name = "badges", length = 50)
    private String badges;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private GoodsItem(Long categoryId, String name, String code, String description,
                      Long price, Long listPrice, Integer stock, Integer notiQty,
                      String soldOut, String useYn, GoodsStatus status,
                      Integer saleCount, Long viewCount, String thumbnailKey,
                      String badges, LocalDateTime createdAt) {
        this.categoryId = categoryId;
        this.name = name;
        this.code = code;
        this.description = description;
        this.price = price;
        this.listPrice = listPrice;
        this.stock = stock != null ? stock : 0;
        this.notiQty = notiQty != null ? notiQty : 0;
        this.soldOut = soldOut;
        this.useYn = useYn;
        this.status = status;
        this.saleCount = saleCount != null ? saleCount : 0;
        this.viewCount = viewCount != null ? viewCount : 0L;
        this.thumbnailKey = thumbnailKey;
        this.badges = badges;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    /** Updates editable fields (full edit from the detail form). */
    public void update(Long categoryId, String name, String code, String description,
                       Long price, Long listPrice, Integer stock, Integer notiQty,
                       String soldOut, String useYn, GoodsStatus status,
                       String thumbnailKey, String badges) {
        this.categoryId = categoryId;
        this.name = name;
        this.code = code;
        this.description = description;
        this.price = price;
        this.listPrice = listPrice;
        this.stock = stock;
        this.notiQty = notiQty;
        this.soldOut = soldOut;
        this.useYn = useYn;
        this.status = status;
        this.thumbnailKey = thumbnailKey;
        this.badges = badges;
        this.updatedAt = LocalDateTime.now();
    }

    /** Applies an inline grid edit; {@code null} arguments keep the existing value. */
    public void applyInlineEdit(Integer stock, Integer notiQty, Long price,
                                String soldOut, String useYn) {
        if (stock != null) {
            this.stock = stock;
        }
        if (notiQty != null) {
            this.notiQty = notiQty;
        }
        if (price != null) {
            this.price = price;
        }
        if (soldOut != null) {
            this.soldOut = soldOut;
        }
        if (useYn != null) {
            this.useYn = useYn;
        }
        this.updatedAt = LocalDateTime.now();
    }

}
