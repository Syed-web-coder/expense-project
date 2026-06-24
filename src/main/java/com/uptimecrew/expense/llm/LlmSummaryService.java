package com.uptimecrew.expense.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion.VersionFlag;
import com.networknt.schema.ValidationMessage;
import com.uptimecrew.expense.graphql.MerchantSummary;
import com.uptimecrew.expense.readmodel.MerchantReadModel;
import com.uptimecrew.expense.readmodel.MerchantReadModelRepository;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.io.InputStream;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class LlmSummaryService {

    private static final Logger LOG = LoggerFactory.getLogger(LlmSummaryService.class);

    private static final AttributeKey<String> ATTR_MODEL        = AttributeKey.stringKey("llm.model");
    private static final AttributeKey<String> ATTR_AGGREGATE_ID = AttributeKey.stringKey("llm.input.aggregate_id");
    private static final AttributeKey<Long>   ATTR_TOKENS_IN    = AttributeKey.longKey("llm.tokens.in");
    private static final AttributeKey<Long>   ATTR_TOKENS_OUT   = AttributeKey.longKey("llm.tokens.out");

    private final ChatClient chatClient;
    private final MerchantReadModelRepository readModelRepository;
    private final ObjectMapper mapper;
    private final JsonSchema schema;
    private final Tracer tracer;
    private final String configuredModel;

    public LlmSummaryService(ChatClient.Builder chatClientBuilder,
                             MerchantReadModelRepository readModelRepository,
                             ObjectMapper mapper,
                             OpenTelemetry openTelemetry) {
        this.chatClient = chatClientBuilder.build();
        this.readModelRepository = readModelRepository;
        this.mapper = mapper;
        this.tracer = openTelemetry.getTracer("com.uptimecrew.expense.llm");
        this.configuredModel = "claude-sonnet-4-5";
        try (InputStream in = new ClassPathResource(
                "schemas/MerchantSummary.schema.json").getInputStream()) {
            this.schema = JsonSchemaFactory
                    .getInstance(VersionFlag.V202012)
                    .getSchema(in);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to load MerchantSummary schema", ex);
        }
    }

    public MerchantSummary summarize(String id) {
        MerchantReadModel doc = readModelRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("unknown id " + id));

        String prompt = "Summarise this merchant as a JSON object "
                + "that matches the MerchantSummary schema. Output JSON only, "
                + "no prose. Domain data: mccCode=" + doc.getMccCode()
                + " transactions=" + (doc.getTransactions() == null ? 0 : doc.getTransactions().size());

        Span span = tracer.spanBuilder("llm.summarize")
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute(ATTR_MODEL, configuredModel)
                .setAttribute(ATTR_AGGREGATE_ID, id)
                .startSpan();
        try (Scope ignored = span.makeCurrent()) {
            ChatResponse response = chatClient.prompt().user(prompt).call().chatResponse();
            MerchantSummary result = mapper.readValue(
                    response.getResult().getOutput().getText(),
                    MerchantSummary.class);

            long promptTokens = safeLong(response.getMetadata().getUsage().getPromptTokens());
            long completionTokens = safeLong(response.getMetadata().getUsage().getCompletionTokens());
            span.setAttribute(ATTR_TOKENS_IN, promptTokens);
            span.setAttribute(ATTR_TOKENS_OUT, completionTokens);

            validate(result);
            span.setStatus(StatusCode.OK);
            LOG.info("structured-output ok id={} model={} tokens.in={} tokens.out={}",
                     id, configuredModel, promptTokens, completionTokens);
            return result;
        } catch (RuntimeException ex) {
            span.recordException(ex);
            span.setStatus(StatusCode.ERROR, ex.getClass().getSimpleName());
            throw ex;
        } catch (Exception ex) {
            span.recordException(ex);
            span.setStatus(StatusCode.ERROR, ex.getClass().getSimpleName());
            throw new IllegalStateException("LLM call failed", ex);
        } finally {
            span.end();
        }
    }

    private static long safeLong(Number n) {
        return n == null ? 0L : n.longValue();
    }

    private void validate(MerchantSummary candidate) {
        try {
            JsonNode node = mapper.valueToTree(candidate);
            Set<ValidationMessage> errors = schema.validate(node);
            if (!errors.isEmpty()) {
                LOG.warn("structured-output schema violation errors={}", errors);
                throw new IllegalStateException(
                        "LLM output failed JSON Schema validation: " + errors);
            }
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("JSON Schema validation threw", ex);
        }
    }
}
