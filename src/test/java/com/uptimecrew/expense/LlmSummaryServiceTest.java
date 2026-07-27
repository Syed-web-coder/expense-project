package com.uptimecrew.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uptimecrew.expense.entity.MerchantEntity;
import com.uptimecrew.expense.graphql.MerchantSummary;
import com.uptimecrew.expense.llm.LlmSummaryService;
import com.uptimecrew.expense.llmproxy.cost.CostObserver;
import com.uptimecrew.expense.repository.MerchantRepository;
import io.opentelemetry.api.OpenTelemetry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LlmSummaryServiceTest {

    @Mock
    MerchantRepository repo;

    @Mock
    CostObserver costMiddleware;

    @Test
    void summarize_tokenCounts_matchStubValues() {
        when(repo.findById("m1")).thenReturn(Optional.of(
                new MerchantEntity("m1", "Test Merchant", "5411", Instant.now())));

        LlmSummaryService service = new LlmSummaryService(
                StubChatClientFactory.builderReturning(new MerchantSummary(
                        "5411", new BigDecimal("100.00"), 3, "Grocery",
                        MerchantSummary.Confidence.HIGH)),
                repo,
                new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false),
                OpenTelemetry.noop(),
                costMiddleware);

        MerchantSummary result = service.summarize("m1");

        assertThat(result.tokensIn())
                .as("tokensIn should be non-negative and match the stub's 17 prompt tokens")
                .isEqualTo(17);
        assertThat(result.tokensOut())
                .as("tokensOut should be non-negative and match the stub's 42 completion tokens")
                .isEqualTo(42);
    }
}
