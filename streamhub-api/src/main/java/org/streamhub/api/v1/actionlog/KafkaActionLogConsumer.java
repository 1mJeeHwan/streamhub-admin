package org.streamhub.api.v1.actionlog;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.streamhub.api.v1.actionlog.dto.ActionLogMessage;

/**
 * Consumes action events from Kafka and persists them via {@link ActionLogWriter}. Active when
 * {@code app.eventlog.transport=kafka}. At-least-once delivery (offset committed after a successful
 * write), so a redelivery on rebalance/retry can re-insert a row — acceptable for an audit trail and
 * matching the SQS path's semantics. The consumer group lets multiple instances share partitions.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.eventlog.transport", havingValue = "kafka")
public class KafkaActionLogConsumer {

    private final ActionLogWriter writer;

    public KafkaActionLogConsumer(ActionLogWriter writer) {
        this.writer = writer;
    }

    @KafkaListener(topics = "${app.kafka.action-log-topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void handle(ActionLogMessage message) {
        writer.write(message);
    }
}
