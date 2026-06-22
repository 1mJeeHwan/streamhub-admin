package org.streamhub.api.v1.church.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.streamhub.api.v1.church.entity.Denomination;

/**
 * Full church detail. Base fields from MyBatis; {@code worshipTimes}/{@code thumbnailUrl}/
 * {@code demoData} are filled by the service. {@code demoData} is {@code true} when
 * {@code dataSource == "SEED"} and drives the user-site demo badge.
 */
@Getter
@Setter
@NoArgsConstructor
public class ChurchDetail {
    private Long id;
    private String name;
    private Denomination denomination;
    private Long regionId;
    private String regionName;
    private String address;
    private String addressDetail;
    private String zipcode;
    private String phone;
    private String pastorName;
    private String facilities;
    private String introduction;
    private String homepageUrl;
    private Double latitude;
    private Double longitude;
    private String thumbnailKey;
    private String thumbnailUrl;
    private String dataSource;
    private String openYn;
    private String useYn;
    private boolean demoData;
    /** Members whose home church is this one (drill-down → 회원 목록). Set by the service. */
    private long memberCount;
    /** Worship-service registrations for this church (drill-down → 예배신청 목록). Set by the service. */
    private long worshipRegistrationCount;
    private List<WorshipTimeDto> worshipTimes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
