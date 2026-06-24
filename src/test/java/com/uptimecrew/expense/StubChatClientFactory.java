package com.uptimecrew.expense;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uptimecrew.expense.graphql.MerchantSummary;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

final class StubChatClientFactory {

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
        when(callSpec.entity(MerchantSummary.class)).thenAnswer(inv -> NEXT.get());

        // Task 3 switched LlmSummaryService from .entity() to .chatResponse()
        // (needed to read token usage off the response for the llm.summarize
        // span attributes). This stub previously only covered .entity() --
        // MerchantGraphQlIT's two LLM tests would NPE without this. Built
        // lazily inside thenAnswer so it always reflects the current NEXT
        // value, same as the .entity() stub above (StubChatClientFactory.next()
        // can be called mid-test to change the next response).
        ObjectMapper localMapper = new ObjectMapper();
        when(callSpec.chatResponse()).thenAnswer(inv -> {
            MerchantSummary current = NEXT.get();
            String json;
            try {
                json = localMapper.writeValueAsString(current);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            // AssistantMessage/Generation/ChatResponse/ChatResponseMetadata are
            // concrete classes -- this project's Mockito mock-maker (proxy-based,
            // configured in Week 1 for Java 24) can only mock interfaces, so we
            // build real instances instead. Usage IS an interface, so it's still
            // mocked.
            AssistantMessage output = new AssistantMessage(json);
            Generation generation = new Generation(output);

            Usage usage = mock(Usage.class);
            when(usage.getPromptTokens()).thenReturn(17);
            when(usage.getCompletionTokens()).thenReturn(42);

            ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                    .usage(usage)
                    .build();

            ChatResponse response = new ChatResponse(java.util.List.of(generation), metadata);
            return response;
        });

        return builder;
    }
}
