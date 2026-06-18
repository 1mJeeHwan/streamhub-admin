package org.streamhub.api.v1.store.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.streamhub.api.v1.store.entity.Store;

/**
 * An offline store row. Used as both the admin create/update input and the list/detail
 * output. {@code distanceKm} is filled only by the public distance-sorted listing. All
 * values are demo/fictional (PII guard). Mutable to match the project DTO style.
 *
 * <p>Bean Validation constraints guard the admin create/update body; they only fire on
 * {@code @Valid} request bodies and are inert on the list/detail output path.
 */
@Getter
@Setter
@NoArgsConstructor
public class StoreDto {
    private Long id;

    @NotNull(message = "지역은 필수입니다")
    private Long regionId;

    @NotBlank(message = "매장명을 입력하세요")
    @Size(max = 120, message = "매장명은 120자 이내여야 합니다")
    private String name;

    @Size(max = 300, message = "주소는 300자 이내여야 합니다")
    private String address;

    @Size(max = 30, message = "전화번호는 30자 이내여야 합니다")
    private String phone;

    @NotNull(message = "위도는 필수입니다")
    @DecimalMin(value = "-90.0", message = "위도는 -90 이상이어야 합니다")
    @DecimalMax(value = "90.0", message = "위도는 90 이하여야 합니다")
    private BigDecimal lat;

    @NotNull(message = "경도는 필수입니다")
    @DecimalMin(value = "-180.0", message = "경도는 -180 이상이어야 합니다")
    @DecimalMax(value = "180.0", message = "경도는 180 이하여야 합니다")
    private BigDecimal lng;

    @NotBlank(message = "영업시간을 입력하세요")
    @Size(max = 120, message = "영업시간은 120자 이내여야 합니다")
    private String openHours;

    @Size(max = 1, message = "사용여부는 Y 또는 N 한 글자여야 합니다")
    private String useYn;
    private Double distanceKm; // filled by the public distance-sorted listing
    private LocalDateTime createdAt;

    /** Builds a DTO from a persisted store. */
    public static StoreDto from(Store store) {
        StoreDto dto = new StoreDto();
        dto.id = store.getId();
        dto.regionId = store.getRegionId();
        dto.name = store.getName();
        dto.address = store.getAddress();
        dto.phone = store.getPhone();
        dto.lat = store.getLat();
        dto.lng = store.getLng();
        dto.openHours = store.getOpenHours();
        dto.useYn = store.getUseYn();
        dto.createdAt = store.getCreatedAt();
        return dto;
    }
}
