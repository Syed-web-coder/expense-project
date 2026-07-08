package com.uptimecrew.expense.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

import java.util.Map;
import java.util.Optional;

public final class MerchantLookupHandler
        implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private static final Logger LOG = LoggerFactory.getLogger(MerchantLookupHandler.class);
    private static final String TABLE = System.getenv("MERCHANTS_TABLE");
    private static final String FUNCTION_NAME =
            Optional.ofNullable(System.getenv("AWS_LAMBDA_FUNCTION_NAME")).orElse("local");
    // Explicit region: avoids AWS SDK's region-provider chain, which has
    // nothing to resolve in CI/local test runs (no AWS_REGION env var there).
    // Real Lambda auto-injects AWS_REGION, so this is a safe override either way.
    private static final DynamoDbClient DYNAMO =
            DynamoDbClient.builder().region(Region.US_EAST_1).build();
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context ctx) {
        String correlationId = Optional.ofNullable(event.getHeaders())
                .map(h -> h.get("x-correlation-id"))
                .orElseGet(ctx::getAwsRequestId);

        Map<String, String> pathParams = event.getPathParameters();
        String merchantId = pathParams == null ? null : pathParams.get("merchantId");

        if (merchantId == null || merchantId.isBlank()) {
            LOG.warn("correlationId={} missing merchantId", correlationId);
            return errorResponse(400, "merchantId path parameter is required", correlationId);
        }

        LOG.info("correlationId={} merchantId={} lookup start", correlationId, merchantId);

        Optional<MerchantRecord> found = loadFromDynamo(merchantId);
        if (found.isEmpty()) {
            LOG.info("correlationId={} merchantId={} not found", correlationId, merchantId);
            emitEmf("MerchantNotFound", 1);
            return errorResponse(404, "Merchant not found: " + merchantId, correlationId);
        }

        try {
            String body = MAPPER.writeValueAsString(found.get());
            LOG.info("correlationId={} merchantId={} found", correlationId, merchantId);
            emitEmf("MerchantLookupSuccess", 1);
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(200)
                    .withHeaders(Map.of(
                            "Content-Type", "application/json",
                            "x-correlation-id", correlationId))
                    .withBody(body)
                    .build();
        } catch (Exception e) {
            LOG.error("correlationId={} serialization failed", correlationId, e);
            return errorResponse(500, "Internal error", correlationId);
        }
    }

    private static Optional<MerchantRecord> loadFromDynamo(String merchantId) {
        GetItemResponse response = DYNAMO.getItem(GetItemRequest.builder()
                .tableName(TABLE)
                .key(Map.of("id", AttributeValue.fromS(merchantId)))
                .build());

        if (!response.hasItem() || response.item().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(MerchantRecord.fromItem(response.item()));
    }

    private static void emitEmf(String metricName, int value) {
        long ts = System.currentTimeMillis();
        // Hand-written EMF JSON — no extra dependency; Lambda log agent extracts metrics from it.
        System.out.printf(
            "{\"_aws\":{\"Timestamp\":%d,\"CloudWatchMetrics\":[{\"Namespace\":\"ExpenseDev\"," +
            "\"Dimensions\":[[\"FunctionName\"]],\"Metrics\":[{\"Name\":\"%s\",\"Unit\":\"Count\"}]}]}," +
            "\"FunctionName\":\"%s\",\"%s\":%d}%n",
            ts, metricName, FUNCTION_NAME, metricName, value);
    }

    private static APIGatewayV2HTTPResponse errorResponse(int statusCode, String message,
                                                           String correlationId) {
        String body = "{\"error\":\"" + message.replace("\"", "\\\"") + "\"}";
        return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(statusCode)
                .withHeaders(Map.of(
                        "Content-Type", "application/json",
                        "x-correlation-id", correlationId))
                .withBody(body)
                .build();
    }
}
