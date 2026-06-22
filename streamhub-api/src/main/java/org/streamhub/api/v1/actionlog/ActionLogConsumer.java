package org.streamhub.api.v1.actionlog;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.streamhub.api.v1.actionlog.dto.ActionLogMessage;

/**
 * Consumes action events from SQS and persists them via {@link ActionLogWriter}. Active when
 * {@code app.eventlog.transport=sqs} or unset ({@code matchIfMissing=true}), so the SQS pipeline is
 * the default. The Kafka path uses {@link KafkaActionLogConsumer} instead.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.eventlog.transport", havingValue = "sqs", matchIfMissing = true)
public class ActionLogConsumer {

    private final ActionLogWriter writer;

    public ActionLogConsumer(ActionLogWriter writer) {
        this.writer = writer;
    }

    @SqsListener("${app.sqs.action-log-queue}")
    public void handle(ActionLogMessage message) {
        writer.write(message);
    }
}
