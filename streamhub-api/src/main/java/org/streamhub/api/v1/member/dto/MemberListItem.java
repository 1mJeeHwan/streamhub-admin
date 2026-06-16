package org.streamhub.api.v1.member.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.streamhub.api.v1.member.entity.UserStatus;

/**
 * One row of the member list, joined with church/region/country names.
 * Populated by MyBatis ({@code MemberMapper.selectList}).
 */
@Getter
@Setter
@NoArgsConstructor
public class MemberListItem {
    private Long id;
    private String name;
    private String email;
    private String phone;
    private UserStatus userStatus;
    private String liveYn;
    private Long churchId;
    private String churchName;
    private String regionName;
    private String countryName;
    private LocalDateTime createdAt;
}
