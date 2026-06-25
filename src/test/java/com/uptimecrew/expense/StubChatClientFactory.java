package com.uptimecrew.expense;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uptimecrew.expense.graphql.MerchantSummary;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

final class StubChatClientFactory {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AtomicReference<MerchantSummary> NEXT = new AtomicReference<>();

    private StubChatClientFactory() {}

    static void next(MerchantSummary value) {
        NEXT.set(value);
    }

    static ChatClient.Builder builderReturning(MerchantSummary value) {
        NEXT.set(value);

        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient client = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);

        when(builder.build()).thenReturn(client);
        when(client.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.chatResponse()).thenAnswer(inv -> buildChatResponse(NEXT.get()));

        return builder;
    }

    // W3 D5: LlmSummaryService.summarize now calls .chatResponse() (not .entity(...))
    // so it can read token usage off the metadata too. We build a real
    // ChatResponse/Generation/AssistantMessage/DefaultUsage tree rather than mocking
    // them -- they're plain value objects, so constructing real ones is more robust
    // than mocking each accessor by hand. Deterministic token counts: 17 in, 42 out.
    private static ChatResponse buildChatResponse(MerchantSummary summary) {
        try {
            String json = MAPPER.writeValueAsString(summary);
            AssistantMessage message = new AssistantMessage(json);
            Generation generation = new Generation(message);
            ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                    .usage(new DefaultUsage(17, 42))
                    .build();
            return new ChatResponse(List.of(generation), metadata);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to build stub ChatResponse", ex);
        }
    }
}
