package org.streamhub.api.v1.actionlog;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.streamhub.api.v1.actionlog.dto.ActionLogMessage;

/**
 * Default action-log transport — publishes to AWS SQS (LocalStack locally). Active when
 * {@code app.eventlog.transport=sqs} or unset ({@code matchIfMissing=true}), so existing deploys
 * keep working unchanged. Best-effort: a send failure is logged, never thrown.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.eventlog.transport", havingValue = "sqs", matchIfMissing = true)
public class SqsActionLogTransport implements ActionLogTransport {

    private final SqsTemplate sqsTemplate;
    private final String queueName;

    public SqsActionLogTransport(SqsTemplate sqsTemplate,
                                 @Value("${app.sqs.action-log-queue}") String queueName) {
        this.sqsTemplate = sqsTemplate;
        this.queueName = queueName;
    }

    @Override
    public void send(ActionLogMessage message) {
        try {
            sqsTemplate.send(queueName, message);
        } catch (RuntimeException e) {
            log.warn("Failed to publish action log to SQS [{}]: {}", message.action(), e.getMessage());
        }
    }
}
