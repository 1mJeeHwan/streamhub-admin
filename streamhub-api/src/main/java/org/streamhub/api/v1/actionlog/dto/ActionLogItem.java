package org.streamhub.api.v1.actionlog.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One row of the audit-log list (MyBatis). */
@Getter
@Setter
@NoArgsConstructor
public class ActionLogItem {
    private Long id;
    private Long adminId;
    private String adminName;
    private String action;
    private String targetType;
    private String targetId;
    private String detail;
    private LocalDateTime createdAt;
}
