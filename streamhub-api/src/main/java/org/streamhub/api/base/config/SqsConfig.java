package org.streamhub.api.base.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;

/**
 * Ensures the action-log queue exists at startup. Idempotent — {@code createQueue}
 * returns the existing queue if present, so it is safe both against LocalStack
 * locally and a Terraform-created queue in production. Only active on the SQS transport
 * ({@code app.eventlog.transport=sqs}, the default); the Kafka path needs no SQS.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "app.eventlog.transport", havingValue = "sqs", matchIfMissing = true)
public class SqsConfig {

    @Bean
    public ApplicationRunner ensureActionLogQueue(
            SqsAsyncClient sqsAsyncClient,
            @Value("${app.sqs.action-log-queue}") String queueName) {
        return args -> {
            sqsAsyncClient.createQueue(CreateQueueRequest.builder().queueName(queueName).build()).join();
            log.info("SQS queue ready: {}", queueName);
        };
    }
}
