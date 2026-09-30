package com.quotagate.provider;

import com.quotagate.api.dto.ChatRequest;
import com.quotagate.api.dto.ChatResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class MockResponseFactory {

    private MockResponseFactory() {
    }

    static ChatResponse create(ChatRequest request, String provider) {
        String content = "Hello from " + provider + "!";
        int inputTokens = request.messages().stream()
                .mapToInt(message -> message.content().length())
                .sum();
        int outputTokens = content.length();

        return new ChatResponse(
                "chatcmpl-" + provider + "-" + UUID.randomUUID(),
                "chat.completion",
                Instant.now().getEpochSecond(),
                request.model(),
                List.of(new ChatResponse.Choice(
                        0,
                        new ChatResponse.Message("assistant", content),
                        "stop"
                )),
                new ChatResponse.Usage(
                        inputTokens,
                        outputTokens,
                        inputTokens + outputTokens
                )
        );
    }
}