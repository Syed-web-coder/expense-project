package com.uptimecrew.expense.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes TransactionPlaced events from expense.transactions.v1.
 *
 * Intentionally minimal: logs successfully-parsed events and stops there.
 * The point of this listener (for Day 3's purposes) is to exercise the
 * DLQ path in KafkaErrorHandlingConfig — a message that fails to parse as JSON throws
 * out of this method, and Spring Kafka's DefaultErrorHandler +
 * DeadLetterPublishingRecoverer take it from there, routing it to
 * expense.transactions.v1.dlq instead of retrying forever or blocking
 * the partition.
 */
@Component
public class TransactionPlacedListener {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionPlacedListener.class);

    // TextMapGetter bridges Kafka Headers to the OTel propagation API.
    // TextMapGetter has two abstract methods so it cannot be a lambda.
    private static final TextMapGetter<Headers> HEADERS_GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Headers carrier) {
            List<String> names = new ArrayList<>();
            carrier.forEach(h -> names.add(h.key()));
            return names;
        }

        @Override
        public String get(Headers carrier, String key) {
            var header = carrier.lastHeader(key);
            return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
        }
    };

    private final ObjectMapper objectMapper;

    public TransactionPlacedListener(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "expense.transactions.v1", groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(ConsumerRecord<String, String> record) throws Exception {
        // Extract the W3C traceparent header injected by OutboxPoller so this
        // consumer span continues the producer's trace instead of starting a
        // new root trace.
        Context parentContext = GlobalOpenTelemetry.getPropagators()
                .getTextMapPropagator()
                .extract(Context.current(), record.headers(), HEADERS_GETTER);
        Span span = GlobalOpenTelemetry.getTracer("com.uptimecrew.expense.events")
                .spanBuilder("transaction.placed.process")
                .setParent(parentContext)
                .startSpan();
        try (Scope ignored = span.makeCurrent()) {
            // Throws JsonProcessingException (a permanent, non-retryable
            // failure per Topic 8) if the payload isn't valid JSON for this type.
            // That exception is what routes the message to the DLQ.
            TransactionPlacedEvent event = objectMapper.readValue(record.value(),
                    TransactionPlacedEvent.class);
            LOG.info("Consumed TransactionPlaced for transactionId={}", event.transactionId());
        } finally {
            span.end();
        }
    }

}
