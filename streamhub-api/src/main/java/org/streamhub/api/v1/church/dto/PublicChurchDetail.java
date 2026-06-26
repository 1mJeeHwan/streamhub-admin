package org.streamhub.api.v1.church.dto;

import java.util.List;
import org.streamhub.api.v1.church.entity.Denomination;

/**
 * Church detail exposed to the <b>public</b> user site. A curated subset of {@link ChurchDetail} —
 * it deliberately omits internal/operational fields that anonymous callers must not see: the
 * raw storage key ({@code thumbnailKey}), the admin drill-down counts ({@code memberCount},
 * {@code worshipRegistrationCount}), the {@code dataSource}/{@code openYn}/{@code useYn} flags, and
 * audit timestamps.
 */
public record PublicChurchDetail(
        Long id,
        String name,
        Denomination denomination,
        String regionName,
        String address,
        String addressDetail,
        String zipcode,
        String phone,
        String pastorName,
        String facilities,
        String introduction,
        String homepageUrl,
        Double latitude,
        Double longitude,
        String thumbnailUrl,
        boolean demoData,
        List<WorshipTimeDto> worshipTimes) {

    /** Projects the full (admin) detail down to the public-safe fields. */
    public static PublicChurchDetail from(ChurchDetail d) {
        return new PublicChurchDetail(
                d.getId(), d.getName(), d.getDenomination(), d.getRegionName(),
                d.getAddress(), d.getAddressDetail(), d.getZipcode(), d.getPhone(),
                d.getPastorName(), d.getFacilities(), d.getIntroduction(), d.getHomepageUrl(),
                d.getLatitude(), d.getLongitude(), d.getThumbnailUrl(), d.isDemoData(),
                d.getWorshipTimes());
    }
}
