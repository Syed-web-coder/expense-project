package com.uptimecrew.expense;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion.VersionFlag;
import com.networknt.schema.ValidationMessage;
import com.uptimecrew.expense.graphql.MerchantGraphQlController;
import com.uptimecrew.expense.graphql.MerchantSummary;
import com.uptimecrew.expense.readmodel.MerchantReadModel;
import com.uptimecrew.expense.readmodel.MerchantReadModelRepository;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureHttpGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureHttpGraphQlTester
@Testcontainers
@ActiveProfiles("test")
class MerchantGraphQlIT {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container @ServiceConnection
    static MongoDBContainer mongo =
            new MongoDBContainer(DockerImageName.parse("mongo:7"));

    @Container @ServiceConnection(name = "redis")
    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @Container @ServiceConnection
    static KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @Autowired HttpGraphQlTester graphQlTester;
    @Autowired ObjectMapper mapper;

    @TestConfiguration
    static class StubChatClientConfig {
        @Bean @Primary
        ChatClient.Builder stubChatClientBuilder() {
            return StubChatClientFactory.builderReturning(new MerchantSummary(
                    "STUB", new BigDecimal("100.00"), 3, "STUB",
                    MerchantSummary.Confidence.HIGH));
        }
    }

    @BeforeAll
    static void seed(@Autowired MerchantReadModelRepository repo) {
        repo.deleteAll();
        for (int i = 1; i <= 3; i++) {
            List<MerchantReadModel.EmbeddedLine> lines = List.of(
                    new MerchantReadModel.EmbeddedLine(1, new BigDecimal("10.00")),
                    new MerchantReadModel.EmbeddedLine(2, new BigDecimal("20.00")));
            repo.save(new MerchantReadModel("seeded-id-" + i, "5411",
                    Instant.now().minusSeconds(i), lines));
        }
    }

    @BeforeEach
    void resetStub() {
        StubChatClientFactory.next(new MerchantSummary(
                "STUB", new BigDecimal("100.00"), 3, "STUB",
                MerchantSummary.Confidence.HIGH));
    }

    @Test
    void lookupById_returnsExpectedFields() {
        graphQlTester
                .document("query($id: ID!) { merchant(id: $id) { id } }")
                .variable("id", "seeded-id-1")
                .execute()
                .path("merchant.id").entity(String.class).isEqualTo("seeded-id-1");
    }

    @Test
    void latestN_returns_batched_lines() {
        ListAppender<ILoggingEvent> appender = attachAppender(MerchantGraphQlController.class);

        graphQlTester
                .document("{ latestMerchants(limit: 3) { id lines { line amount } } }")
                .execute()
                .path("latestMerchants").entityList(Object.class).hasSize(3);

        long batchLogs = appender.list.stream()
                .filter(e -> e.getFormattedMessage().startsWith("batch-loaded "))
                .count();
        assertThat(batchLogs).isEqualTo(1L);
    }

    @Test
    void summarize_returnsStructured_andValidatesSchema() throws Exception {
        var response = graphQlTester
                .document("mutation($id: ID!) { summarizeMerchant(id: $id) { "
                        + "mccCode totalSpend transactionCount primaryCategory confidence "
                        + "tokensIn tokensOut } }")
                .variable("id", "seeded-id-1")
                .execute();

        MerchantSummary payload = response
                .path("summarizeMerchant").entity(MerchantSummary.class).get();

        assertThat(payload).isNotNull();
        assertThat(payload.confidence()).isEqualTo(MerchantSummary.Confidence.HIGH);

        JsonNode node = mapper.valueToTree(payload);
        try (InputStream in = new ClassPathResource("schemas/MerchantSummary.schema.json").getInputStream()) {
            JsonSchema schema = JsonSchemaFactory.getInstance(VersionFlag.V202012).getSchema(in);
            Set<ValidationMessage> errors = schema.validate(node);
            assertThat(errors).isEmpty();
        }

        // StubChatClientFactory.buildChatResponse uses DefaultUsage(17, 42).
        response.path("summarizeMerchant.tokensIn").entity(Integer.class).isEqualTo(17);
        response.path("summarizeMerchant.tokensOut").entity(Integer.class).isEqualTo(42);
    }

    @Test
    void summarize_failsValidation_onMalformedOutput() {
        StubChatClientFactory.next(new MerchantSummary(
                "BAD", new BigDecimal("-1.00"), 0, "BAD",
                MerchantSummary.Confidence.LOW));

        graphQlTester
                .document("mutation { summarizeMerchant(id: \"seeded-id-1\") { mccCode } }")
                .execute()
                .errors().expect(err -> err.getMessage() != null
                        && err.getMessage().contains("JSON Schema"));
    }

    private static ListAppender<ILoggingEvent> attachAppender(Class<?> clazz) {
        Logger lb = (Logger) LoggerFactory.getLogger(clazz);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        lb.addAppender(appender);
        return appender;
    }
}
