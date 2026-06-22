package org.streamhub.api.v1.actionlog;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.streamhub.api.v1.actionlog.dto.ActionLogMessage;
import org.streamhub.api.v1.actionlog.entity.ActionLog;
import org.streamhub.api.v1.actionlog.repository.ActionLogRepository;
import org.streamhub.api.v1.admin.repository.AdminAccountRepository;

/**
 * Persists a consumed {@link ActionLogMessage} as an {@link ActionLog} row, enriching the operator
 * name from the admin account when the message omits it. Shared by both the SQS consumer
 * ({@link ActionLogConsumer}) and the Kafka consumer ({@link KafkaActionLogConsumer}) so the
 * write path is identical regardless of transport.
 */
@Slf4j
@Service
public class ActionLogWriter {

    private final ActionLogRepository actionLogRepository;
    private final AdminAccountRepository adminRepository;

    public ActionLogWriter(ActionLogRepository actionLogRepository,
                           AdminAccountRepository adminRepository) {
        this.actionLogRepository = actionLogRepository;
        this.adminRepository = adminRepository;
    }

    public void write(ActionLogMessage message) {
        String adminName = resolveAdminName(message);
        actionLogRepository.save(ActionLog.builder()
                .adminId(message.adminId())
                .adminName(adminName)
                .action(message.action())
                .targetType(message.targetType())
                .targetId(message.targetId())
                .detail(message.detail())
                .ip(message.ip())
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
