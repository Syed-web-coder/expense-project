package com.uptimecrew.expense.events;

import com.uptimecrew.expense.entity.OutboxEvent;
import com.uptimecrew.expense.repository.OutboxRepository;
import com.uptimecrew.expense.service.TransactionService;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trails the outbox table and ships unpublished rows to Kafka.
 *
 * Runs on a fixed delay, locking a batch of unpublished rows with
 * FOR UPDATE SKIP LOCKED (see OutboxRepository), publishing each one
 * keyed by aggregateId (preserves per-aggregate ordering), then marking
 * the row published. If the poller crashes after publish but before the
 * mark-published commit, it republishes on the next run — that's the
 * at-least-once guarantee; consumer-side idempotency closes the loop.
 */
@Component
public class OutboxPoller {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxPoller.class);
    private static final int BATCH_SIZE = 100;

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPoller(OutboxRepository outboxRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate    = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${outbox.poll-ms:500}")
    @Transactional
    public void publishBatch() {
        List<OutboxEvent> batch = outboxRepository.findUnpublishedForUpdate(BATCH_SIZE);
        for (OutboxEvent event : batch) {
            publishOne(event);
        }
    }

    private void publishOne(OutboxEvent event) {
        String topic = topicFor(event.getEventType());
        String key   = event.getAggregateId();
        try {
            ProducerRecord<String, String> record =
                    new ProducerRecord<>(topic, key, event.getPayload());
            // Inject the current W3C trace context so the Kafka consumer can
            // start its span as a child of this one rather than a new root.
            GlobalOpenTelemetry.getPropagators().getTextMapPropagator()
                    .inject(Context.current(), record.headers(),
                            (h, name, value) -> h.add(name, value.getBytes(StandardCharsets.UTF_8)));
            // Synchronous wait for the broker ack: on a Kafka outage this
            // loop pauses safely rather than marking unsent events published.
            kafkaTemplate.send(record).get();
            event.markPublished();
            outboxRepository.save(event);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Outbox publish interrupted for event id={}", event.getId());
        } catch (ExecutionException e) {
            LOG.warn("Outbox publish failed for event id={}; will retry next poll", event.getId(), e);
        }
    }

    private String topicFor(String eventType) {
        return switch (eventType) {
            case "TransactionPlaced" -> TransactionService.TOPIC;
            default -> throw new IllegalStateException("Unknown event type: " + eventType);
        };
    }
}
