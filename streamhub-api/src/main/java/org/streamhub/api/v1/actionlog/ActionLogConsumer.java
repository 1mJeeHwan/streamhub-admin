package org.streamhub.api.v1.actionlog;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.streamhub.api.v1.actionlog.dto.ActionLogMessage;
import org.streamhub.api.v1.actionlog.entity.ActionLog;
import org.streamhub.api.v1.actionlog.repository.ActionLogRepository;
import org.streamhub.api.v1.admin.repository.AdminAccountRepository;

/**
 * Consumes action events from SQS and persists them as {@link ActionLog} rows.
 * Enriches the operator name from the admin account when the message omits it.
 */
@Slf4j
@Component
public class ActionLogConsumer {

    private final ActionLogRepository actionLogRepository;
    private final AdminAccountRepository adminRepository;

    public ActionLogConsumer(ActionLogRepository actionLogRepository,
                             AdminAccountRepository adminRepository) {
        this.actionLogRepository = actionLogRepository;
        this.adminRepository = adminRepository;
    }

    @SqsListener("${app.sqs.action-log-queue}")
    public void handle(ActionLogMessage message) {
        String adminName = resolveAdminName(message);
        actionLogRepository.save(ActionLog.builder()
                .adminId(message.adminId())
                .adminName(adminName)
                .action(message.action())
                .targetType(message.targetType())
                .targetId(message.targetId())
                .detail(message.detail())
                .build());
        log.debug("Recorded action log: {} by {}", message.action(), adminName);
    }

    private String resolveAdminName(ActionLogMessage message) {
        if (message.adminName() != null) {
            return message.adminName();
        }
        if (message.adminId() == null) {
            return null;
        }
        return adminRepository.findById(message.adminId())
                .map(a -> a.getName())
                .orElse(null);
    }
}
