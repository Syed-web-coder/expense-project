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
import java.io.InputStream;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class LlmSummaryService {

    private static final Logger LOG = LoggerFactory.getLogger(LlmSummaryService.class);

    private final ChatClient chatClient;
    private final MerchantReadModelRepository readModelRepository;
    private final ObjectMapper mapper;
    private final JsonSchema schema;

    public LlmSummaryService(ChatClient.Builder chatClientBuilder,
                             MerchantReadModelRepository readModelRepository,
                             ObjectMapper mapper) {
        this.chatClient = chatClientBuilder.build();
        this.readModelRepository = readModelRepository;
        this.mapper = mapper;
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

        MerchantSummary result = chatClient.prompt()
                .user(prompt)
                .call()
                .entity(MerchantSummary.class);

        validate(result);
        LOG.info("structured-output ok id={} confidence={}", id, result.confidence());
        return result;
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
