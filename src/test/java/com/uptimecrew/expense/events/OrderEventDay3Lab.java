package com.uptimecrew.expense.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uptimecrew.expense.entity.MerchantEntity;
import com.uptimecrew.expense.entity.OutboxEvent;
import com.uptimecrew.expense.entity.TransactionEntity;
import com.uptimecrew.expense.repository.MerchantRepository;
import com.uptimecrew.expense.repository.OutboxRepository;
import com.uptimecrew.expense.repository.TransactionRepository;
import com.uptimecrew.expense.service.TransactionService;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Day 3 hands-on lab: wire the full event-driven + MCP test matrix for
 * the expense-project's "place a transaction" write path.
 *
 * No Testcontainers anywhere in this class — Docker/Colima is unreliable
 * in this environment (see README's Docker/Testcontainers note). Instead:
 *   - Postgres: the real local instance (localhost:5432), the same one
 *     used every day for ./gradlew bootRun; reachable without Docker.
 *   - Kafka: EmbeddedKafkaBroker — runs in-process inside the test JVM,
 *     no broker container required.
 *
 * Test 6 (MCP) is added in a later commit.
 */
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = { TransactionService.TOPIC, TransactionService.TOPIC + ".dlq" })
@DirtiesContext
class OrderEventDay3Lab {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private org.springframework.kafka.core.KafkaTemplate<String, String> kafkaTemplate;

    private MerchantEntity merchant;

    @DynamicPropertySource
    static void kafkaProps(DynamicPropertyRegistry registry) {
        // EmbeddedKafka starts on a random port; point spring.kafka at it
        // instead of the application.yml default of localhost:9092.
        registry.add("spring.kafka.bootstrap-servers",
                () -> System.getProperty("spring.embedded.kafka.brokers"));
    }

    @BeforeEach
    void seedMerchant() {
        merchant = merchantRepository.save(new MerchantEntity(
                "merch-day3-lab-" + UUID.randomUUID(),
                "Day3 Lab Merchant",
                "5411",
                Instant.now()
        ));
    }

    @AfterEach
    void cleanUpMerchant() {
        // Don't rely on cascading through the in-memory `merchant` field —
        // its `transactions` collection was loaded before recordTransaction()
        // created new rows elsewhere, so it's stale and confuses Hibernate's
        // cascade-delete. Delete child rows explicitly, then the merchant.
        transactionRepository.findAll().stream()
                .filter(t -> merchant.getId().equals(t.getMerchant().getId()))
                .forEach(transactionRepository::delete);
        merchantRepository.deleteById(merchant.getId());
    }

    // --- Test 1: producerWritesOutboxAndPublishes -------------------------
    //
    // Placing a transaction writes ONE outbox row + (once the poller runs)
    // publishes ONE TransactionPlaced event with the right key and shape.
    @Test
    void producerWritesOutboxAndPublishes() {
        TransactionEntity saved = transactionService.recordTransaction(
                merchant.getId(), new BigDecimal("42.50"), "DEBIT");

        // 1a. Exactly one outbox row was written for this transaction.
        List<OutboxEvent> outboxRows = outboxRepository.findAll().stream()
                .filter(e -> e.getAggregateId().equals(saved.getId()))
                .toList();
        assertThat(outboxRows).hasSize(1);
        assertThat(outboxRows.get(0).getEventType()).isEqualTo("TransactionPlaced");
        assertThat(outboxRows.get(0).getPayload()).contains(saved.getId());

        // 1b. The OutboxPoller (running on its @Scheduled cadence) ships it
        // to Kafka with the transaction id as the key, within a few seconds.
        try (Consumer<String, String> consumer = buildTestConsumer()) {
            embeddedKafka.consumeFromAnEmbeddedTopic(consumer, TransactionService.TOPIC);

            ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(
                    consumer, TransactionService.TOPIC, Duration.ofSeconds(10));

            assertThat(record.key()).isEqualTo(saved.getId());
            assertThat(record.value()).contains(saved.getId());
            assertThat(record.value()).contains("\"kind\"").contains("DEBIT");
        }

        // 1c. The outbox row itself is eventually marked published.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            OutboxEvent refreshed = outboxRepository.findById(outboxRows.get(0).getId()).orElseThrow();
            assertThat(refreshed.isPublished()).isTrue();
        });

        outboxRepository.deleteAll(outboxRows);
    }

    // --- Test 3: rollbackPreventsPublish -----------------------------------
    //
    // If the place-transaction transaction rolls back, NEITHER the
    // TransactionEntity NOR the OutboxEvent row should exist afterwards.
    // No outbox row ever existing is the strongest possible guarantee that
    // nothing will be published — the poller can only ship rows that exist.
    @Test
    void rollbackPreventsPublish() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        final String[] transactionId = new String[1];

        // recordTransaction runs in its own @Transactional method, but
        // TransactionTemplate.execute here forces the OUTER call context
        // to roll back after the inner writes have happened — simulating
        // a crash/exception right after the DB commit point.
        assertThatThrownBy(() -> tx.execute((TransactionStatus status) -> {
            TransactionEntity saved = transactionService.recordTransaction(
                    merchant.getId(), new BigDecimal("99.99"), "DEBIT");
            transactionId[0] = saved.getId();
            throw new IllegalStateException("forced rollback for test");
        })).isInstanceOf(IllegalStateException.class);

        // Neither row survived the rollback.
        assertThat(transactionRepository.findById(transactionId[0])).isEmpty();
        assertThat(outboxRepository.findAll())
                .noneMatch(e -> e.getAggregateId().equals(transactionId[0]));

        // Give the poller a beat to prove there's nothing it COULD have
        // published — there's no outbox row, so no record should ever
        // appear keyed by this transaction id.
        try (Consumer<String, String> consumer = buildTestConsumer()) {
            embeddedKafka.consumeFromAnEmbeddedTopic(consumer, TransactionService.TOPIC);
            var records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(3));
            boolean anyMatchingKey = false;
            for (ConsumerRecord<String, String> record : records.records(TransactionService.TOPIC)) {
                if (transactionId[0].equals(record.key())) {
                    anyMatchingKey = true;
                }
            }
            assertThat(anyMatchingKey).isFalse();
        }
    }

    // --- Test 4: v1EventReadByV2Consumer -----------------------------------
    //
    // Publish (serialise) an event with the v1 schema; deserialise it with
    // the v2 schema (which adds one optional field, `category`). Backward
    // compatibility means this must parse cleanly — `category` simply comes
    // back null, since v1 never had it.
    @Test
    void v1EventReadByV2Consumer() throws Exception {
        TransactionPlacedEvent v1Event = new TransactionPlacedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                "txn-schema-evo-001",
                merchant.getId(),
                merchant.getName(),
                new BigDecimal("15.00"),
                Instant.now(),
                "DEBIT"
        );

        String wireFormat = objectMapper.writeValueAsString(v1Event);

        // A v2 consumer reading a v1-shaped event must not throw.
        TransactionPlacedEventV2 v2View =
                objectMapper.readValue(wireFormat, TransactionPlacedEventV2.class);

        assertThat(v2View.transactionId()).isEqualTo(v1Event.transactionId());
        assertThat(v2View.merchantId()).isEqualTo(v1Event.merchantId());
        assertThat(v2View.amount()).isEqualTo(v1Event.amount());
        assertThat(v2View.kind()).isEqualTo(v1Event.kind());
        // The field v1 never had comes back null, not an exception.
        assertThat(v2View.category()).isNull();
    }

    // --- Test 5: malformedEventRoutesToDlq ---------------------------------
    //
    // Publish a malformed (non-JSON) event directly to the topic.
    // TransactionPlacedListener.onMessage() throws JsonProcessingException
    // trying to parse it; KafkaConfig's DefaultErrorHandler +
    // DeadLetterPublishingRecoverer classify that as PERMANENT (Topic 8) and
    // route it to expense.transactions.v1.dlq within 5 seconds, preserving
    // the original topic in the KafkaHeaders.DLT_ORIGINAL_TOPIC header.
    @Test
    void malformedEventRoutesToDlq() {
        String dlqTopic = TransactionService.TOPIC + ".dlq";
        String malformedPayload = "{ this is not valid json !!";
        String key = "malformed-" + UUID.randomUUID();

        kafkaTemplate.send(TransactionService.TOPIC, key, malformedPayload);

        try (Consumer<String, String> dlqConsumer = buildTestConsumer()) {
            embeddedKafka.consumeFromAnEmbeddedTopic(dlqConsumer, dlqTopic);

            ConsumerRecord<String, String> dlqRecord = KafkaTestUtils.getSingleRecord(
                    dlqConsumer, dlqTopic, Duration.ofSeconds(5));

            assertThat(dlqRecord.value()).isEqualTo(malformedPayload);

            Header originalTopicHeader = dlqRecord.headers()
                    .lastHeader(org.springframework.kafka.support.KafkaHeaders.DLT_ORIGINAL_TOPIC);
            assertThat(originalTopicHeader).isNotNull();
            assertThat(new String(originalTopicHeader.value(), StandardCharsets.UTF_8))
                    .isEqualTo(TransactionService.TOPIC);
        }
    }

    private Consumer<String, String> buildTestConsumer() {
        Map<String, Object> props = KafkaTestUtils.consumerProps(
                "day3-lab-test-group-" + UUID.randomUUID(), "true", embeddedKafka);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<String, String>(props).createConsumer();
    }
}
