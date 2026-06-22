package org.streamhub.api.v1.actionlog;

import org.streamhub.api.v1.actionlog.dto.ActionLogMessage;

/**
 * Transport seam for the async action-log pipeline (best-effort, fire-and-forget). The default
 * {@link SqsActionLogTransport} publishes to AWS SQS; {@link KafkaActionLogTransport} publishes to
 * Apache Kafka. The active implementation is chosen by {@code app.eventlog.transport} (sqs | kafka),
 * mirroring the project's seam philosophy: swap the bean via config, never the call sites.
 *
 * <p>Implementations must be <b>non-throwing</b> — a messaging failure is swallowed/logged so it can
 * never break the underlying business transaction that emitted the event.
 */
public interface ActionLogTransport {

    /** Publishes one action-log message. Must not throw. */
    void send(ActionLogMessage message);
}
