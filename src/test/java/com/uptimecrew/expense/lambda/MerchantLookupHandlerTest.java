package com.uptimecrew.expense.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantLookupHandlerTest {

    private final MerchantLookupHandler handler = new MerchantLookupHandler();

    @Mock
    Context ctx;

    @Test
    void returns400OnMissingPathParam() {
        when(ctx.getAwsRequestId()).thenReturn("aws-req-id-001");
        APIGatewayV2HTTPEvent event = APIGatewayV2HTTPEvent.builder().build();

        APIGatewayV2HTTPResponse response = handler.handleRequest(event, ctx);

        assertEquals(400, response.getStatusCode());
    }

    @Test
    void echoesCallerCorrelationIdWhenSupplied() {
        String correlationId = "caller-corr-id-xyz";
        APIGatewayV2HTTPEvent event = APIGatewayV2HTTPEvent.builder()
                .withHeaders(Map.of("x-correlation-id", correlationId))
                .build();

        APIGatewayV2HTTPResponse response = handler.handleRequest(event, ctx);

        assertEquals(400, response.getStatusCode());
        assertEquals(correlationId, response.getHeaders().get("x-correlation-id"));
    }
}
