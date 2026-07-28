package com.uptimecrew.expense.config;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Wires the DLQ ("dead-letter queue") path for Kafka consumers.
 *
 * A deserialisation failure (malformed JSON, schema mismatch) is the
 * textbook PERMANENT failure from Topic 8: retrying won't help, because
 * the bytes on the topic never change. DefaultErrorHandler here uses a
 * FixedBackOff of zero retries specifically for that reason — there's
 * nothing to wait for. DeadLetterPublishingRecoverer republishes the
 * failed record (with its original headers, plus exception metadata) to
 * "<original-topic>.dlq" and acks the original so the consumer can move
 * on to the next message rather than getting stuck forever.
 */
@Configuration
public class KafkaErrorHandlingConfig {

    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (ConsumerRecord<?, ?> record, Exception ex) ->
                        // Same partition count assumption as the source topic;
                        // route to "<topic>.dlq" regardless of which topic failed.
                        new org.apache.kafka.common.TopicPartition(
                                record.topic() + ".dlq", record.partition())
        );

        // Zero retries: a malformed/unparseable message will never succeed
        // on retry, so go straight to the DLQ rather than burn time on
        // the consumer thread or block the partition.
        return new DefaultErrorHandler(recoverer, new FixedBackOff(0L, 0L));
    }
}
