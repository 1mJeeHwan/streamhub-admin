package org.streamhub.api.v1.member.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.streamhub.api.v1.member.entity.UserStatus;

/**
 * Full member detail, joined with church/region/country names.
 * Populated by MyBatis ({@code MemberMapper.selectDetail}).
 */
@Getter
@Setter
@NoArgsConstructor
public class MemberDetail {
    private Long id;
    private String name;
    private String email;
    private String phone;
    private UserStatus userStatus;
    private String liveYn;
    private Long churchId;
    private String churchName;
    private Long regionId;
    private String regionName;
    private Long countryId;
    private String countryName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
