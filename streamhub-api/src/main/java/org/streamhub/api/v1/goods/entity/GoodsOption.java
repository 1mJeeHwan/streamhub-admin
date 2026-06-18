package org.streamhub.api.v1.goods.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A selectable option (size/color) of a {@link GoodsItem}, with its own stock. */
@Entity
@Table(name = "GOODS_OPTION", indexes = {
        @Index(name = "idx_goods_option_item", columnList = "item_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoodsOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → GOODS_ITEM. */
    @Column(name = "item_id", nullable = false)
    private Long itemId;

    /** Option label, e.g. "블랙 / L". */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** Option type, e.g. "색상/사이즈". */
    @Column(name = "option_type", length = 50)
    private String optionType;

    /** Extra charge added to the item price (KRW). */
    @Column(name = "extra_price", nullable = false)
    private Long extraPrice;

    @Column(name = "stock", nullable = false)
    private Integer stock;

    /** "Y"/"N". */
    @Column(name = "use_yn", nullable = false, length = 1)
    private String useYn;

    @Column(name = "sort", nullable = false)
    private Integer sort;

    @Builder
    private GoodsOption(Long itemId, String name, String optionType, Long extraPrice,
                        Integer stock, String useYn, Integer sort) {
        this.itemId = itemId;
        this.name = name;
        this.optionType = optionType;
        this.extraPrice = extraPrice != null ? extraPrice : 0L;
        this.stock = stock != null ? stock : 0;
        this.useYn = useYn;
        this.sort = sort != null ? sort : 0;
    }

}
