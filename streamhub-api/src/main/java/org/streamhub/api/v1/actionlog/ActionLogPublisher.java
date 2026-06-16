package org.streamhub.api.v1.actionlog;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.streamhub.api.base.security.AdminPrincipal;
import org.streamhub.api.v1.actionlog.dto.ActionLogMessage;

/**
 * Publishes admin-action events to SQS. The consumer ({@link ActionLogConsumer})
 * persists them. Publishing is best-effort: a messaging failure never breaks the
 * underlying business action.
 */
@Slf4j
@Component
public class ActionLogPublisher {

    private final SqsTemplate sqsTemplate;
    private final String queueName;

    public ActionLogPublisher(SqsTemplate sqsTemplate,
                              @Value("${app.sqs.action-log-queue}") String queueName) {
        this.sqsTemplate = sqsTemplate;
        this.queueName = queueName;
    }

    /** Publish using the currently authenticated operator (from the security context). */
    public void publish(String action, String targetType, String targetId, String detail) {
        AdminPrincipal principal = currentPrincipal();
        Long adminId = principal != null ? principal.id() : null;
        publishAs(adminId, null, action, targetType, targetId, detail);
    }

    /** Publish with an explicit operator (e.g. on login, before the security context exists). */
    public void publishAs(Long adminId, String adminName, String action,
                          String targetType, String targetId, String detail) {
        try {
            sqsTemplate.send(queueName,
                    new ActionLogMessage(adminId, adminName, action, targetType, targetId, detail));
        } catch (RuntimeException e) {
            log.warn("Failed to publish action log [{}]: {}", action, e.getMessage());
        }
    }

    private AdminPrincipal currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AdminPrincipal p) {
            return p;
        }
        return null;
    }
}
