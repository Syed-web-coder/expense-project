package com.uptimecrew.expense.events;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = { TransactionService.TOPIC, TransactionService.TOPIC + ".dlq" })
@DirtiesContext
class OrderEventDay3Lab {

    @Autowired
    private MockMvc mvc;

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
        transactionRepository.findAll().stream()
                .filter(t -> merchant.getId().equals(t.getMerchant().getId()))
                .forEach(transactionRepository::delete);
        merchantRepository.deleteById(merchant.getId());
    }

    @Test
    void producerWritesOutboxAndPublishes() {
        TransactionEntity saved = transactionService.recordTransaction(
                merchant.getId(), new BigDecimal("42.50"), "DEBIT");

        List<OutboxEvent> outboxRows = outboxRepository.findAll().stream()
                .filter(e -> e.getAggregateId().equals(saved.getId()))
                .toList();
        assertThat(outboxRows).hasSize(1);
        assertThat(outboxRows.get(0).getEventType()).isEqualTo("TransactionPlaced");
        assertThat(outboxRows.get(0).getPayload()).contains(saved.getId());

        try (Consumer<String, String> consumer = buildTestConsumer()) {
            embeddedKafka.consumeFromAnEmbeddedTopic(consumer, TransactionService.TOPIC);

            ConsumerRecord<String, String>[] matchHolder = new ConsumerRecord[1];
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                var records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(1));
                for (ConsumerRecord<String, String> r : records.records(TransactionService.TOPIC)) {
                    if (saved.getId().equals(r.key())) {
                        matchHolder[0] = r;
                    }
                }
                assertThat(matchHolder[0]).isNotNull();
            });

            ConsumerRecord<String, String> record = matchHolder[0];
            assertThat(record.key()).isEqualTo(saved.getId());
            assertThat(record.value()).contains(saved.getId());
            assertThat(record.value()).contains("\"kind\"").contains("DEBIT");
        }

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            OutboxEvent refreshed = outboxRepository.findById(outboxRows.get(0).getId()).orElseThrow();
            assertThat(refreshed.isPublished()).isTrue();
        });

        outboxRepository.deleteAll(outboxRows);
    }

    @Test
    void consumerIdempotentOnDuplicate() {
        String idempotencyKey = UUID.randomUUID().toString();

        TransactionEntity first = transactionService.recordTransaction(
                merchant.getId(), new BigDecimal("20.00"), "DEBIT", idempotencyKey);

        TransactionEntity second = transactionService.recordTransaction(
                merchant.getId(), new BigDecimal("20.00"), "DEBIT", idempotencyKey);

        assertThat(second.getId()).isEqualTo(first.getId());

        long transactionCount = transactionRepository.findAll().stream()
                .filter(t -> idempotencyKey.equals(t.getIdempotencyKey()))
                .count();
        assertThat(transactionCount).isEqualTo(1);

        long outboxCount = outboxRepository.findAll().stream()
                .filter(e -> e.getAggregateId().equals(first.getId()))
                .count();
        assertThat(outboxCount).isEqualTo(1);

        outboxRepository.findAll().stream()
                .filter(e -> e.getAggregateId().equals(first.getId()))
                .forEach(outboxRepository::delete);
    }

    @Test
    void rollbackPreventsPublish() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        final String[] transactionId = new String[1];

        assertThatThrownBy(() -> tx.execute((TransactionStatus status) -> {
            TransactionEntity saved = transactionService.recordTransaction(
                    merchant.getId(), new BigDecimal("99.99"), "DEBIT");
            transactionId[0] = saved.getId();
            throw new IllegalStateException("forced rollback for test");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(transactionRepository.findById(transactionId[0])).isEmpty();
        assertThat(outboxRepository.findAll())
                .noneMatch(e -> e.getAggregateId().equals(transactionId[0]));

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

        TransactionPlacedEventV2 v2View =
                objectMapper.readValue(wireFormat, TransactionPlacedEventV2.class);

        assertThat(v2View.transactionId()).isEqualTo(v1Event.transactionId());
        assertThat(v2View.merchantId()).isEqualTo(v1Event.merchantId());
        assertThat(v2View.amount()).isEqualTo(v1Event.amount());
        assertThat(v2View.kind()).isEqualTo(v1Event.kind());
        assertThat(v2View.category()).isNull();
    }

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

    @Test
    void mcpPlaceOrderRespectsScope() throws Exception {
        String initBody = "{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test-client\",\"version\":\"1.0.0\"}}}";

        var initResult = mvc.perform(post("/mcp")
                        .contentType("application/json")
                        .accept("application/json", "text/event-stream")
                        .content(initBody)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_transactions:write"))))
                .andReturn();
        String sessionId = initResult.getResponse().getHeader("Mcp-Session-Id");
        assertThat(sessionId).isNotBlank();

        String validIdempotencyKey = UUID.randomUUID().toString();
        String validScopeBody = String.format(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"place_transaction\",\"arguments\":{\"merchantId\":\"%s\",\"amount\":\"10.00\",\"kind\":\"DEBIT\",\"idempotencyKey\":\"%s\"}}}",
                merchant.getId(), validIdempotencyKey);

        var withScopeResult = mvc.perform(post("/mcp")
                        .contentType("application/json")
                        .accept("application/json", "text/event-stream")
                        .header("Mcp-Session-Id", sessionId)
                        .content(validScopeBody)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_transactions:write"))))
                .andReturn();
        if (withScopeResult.getRequest().isAsyncStarted()) {
            withScopeResult = mvc.perform(asyncDispatch(withScopeResult)).andReturn();
        }
        assertThat(withScopeResult.getResponse().getStatus()).isEqualTo(200);

        JsonNode withScopeRpc = parseSseDataAsJson(withScopeResult.getResponse().getContentAsString());
        assertThat(withScopeRpc.path("result").path("isError").asBoolean()).isFalse();

        String withScopeText = withScopeRpc.path("result").path("content").get(0).path("text").asText();
        JsonNode transactionView = objectMapper.readTree(withScopeText);
        assertThat(transactionView.path("merchantId").asText()).isEqualTo(merchant.getId());
        assertThat(transactionView.path("kind").asText()).isEqualTo("DEBIT");

        String noScopeBody = String.format(
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"place_transaction\",\"arguments\":{\"merchantId\":\"%s\",\"amount\":\"10.00\",\"kind\":\"DEBIT\",\"idempotencyKey\":\"%s\"}}}",
                merchant.getId(), UUID.randomUUID());

        var noScopeResult = mvc.perform(post("/mcp")
                        .contentType("application/json")
                        .accept("application/json", "text/event-stream")
                        .header("Mcp-Session-Id", sessionId)
                        .content(noScopeBody)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_something:else"))))
                .andReturn();
        if (noScopeResult.getRequest().isAsyncStarted()) {
            noScopeResult = mvc.perform(asyncDispatch(noScopeResult)).andReturn();
        }
        assertThat(noScopeResult.getResponse().getStatus()).isEqualTo(200);

        JsonNode noScopeRpc = parseSseDataAsJson(noScopeResult.getResponse().getContentAsString());
        assertThat(noScopeRpc.path("result").path("isError").asBoolean()).isTrue();

        String noScopeText = noScopeRpc.path("result").path("content").get(0).path("text").asText();
        assertThat(noScopeText).containsIgnoringCase("denied");

        transactionRepository.findById(transactionView.path("id").asText())
                .ifPresent(transactionRepository::delete);
        outboxRepository.findAll().stream()
                .filter(e -> e.getAggregateId().equals(transactionView.path("id").asText()))
                .forEach(outboxRepository::delete);
    }

    private JsonNode parseSseDataAsJson(String sseBody) throws Exception {
        String dataLine = sseBody.lines()
                .filter(line -> line.startsWith("data:"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No SSE data line found in: " + sseBody));
        return objectMapper.readTree(dataLine.substring("data:".length()));
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
