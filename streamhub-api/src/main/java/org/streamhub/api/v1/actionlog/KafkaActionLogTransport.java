package org.streamhub.api.v1.actionlog;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.streamhub.api.v1.actionlog.dto.ActionLogMessage;

/**
 * Apache Kafka action-log transport. Active when {@code app.eventlog.transport=kafka}. Publishes the
 * {@link ActionLogMessage} (JSON-serialized) to the configured topic, <b>keyed by operator id</b> so
 * one operator's events always land on the same partition — Kafka guarantees order within a
 * partition, so per-operator action order is preserved. Best-effort: a send failure is logged.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.eventlog.transport", havingValue = "kafka")
public class KafkaActionLogTransport implements ActionLogTransport {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;

    public KafkaActionLogTransport(KafkaTemplate<String, Object> kafkaTemplate,
                                   @Value("${app.kafka.action-log-topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @Override
    public void send(ActionLogMessage message) {
        try {
            String key = message.adminId() != null ? String.valueOf(message.adminId()) : null;
            kafkaTemplate.send(topic, key, message);
        } catch (RuntimeException e) {
            log.warn("Failed to publish action log to Kafka [{}]: {}", message.action(), e.getMessage());
        }
    }
}
