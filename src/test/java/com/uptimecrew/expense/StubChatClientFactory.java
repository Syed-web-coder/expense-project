package com.uptimecrew.expense;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uptimecrew.expense.graphql.MerchantSummary;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.ai.chat.client.ChatClient;

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

        return builder;
    }
}
