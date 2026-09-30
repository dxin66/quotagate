package com.quotagate.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quotagate.api.dto.ChatResponse;

import java.util.List;
import java.util.Map;

final class StreamChunkEncoder {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private StreamChunkEncoder() {
    }

    static String encode(ChatResponse response) {
        ChatResponse.Choice choice = response.choices().getFirst();
        Map<String, Object> chunk = Map.of(
                "id", response.id(),
                "object", "chat.completion.chunk",
                "created", response.created(),
                "model", response.model(),
                "choices", List.of(Map.of(
                        "index", choice.index(),
                        "delta", Map.of(
                                "role", choice.message().role(),
                                "content", choice.message().content()
                        ),
                        "finish_reason", choice.finishReason()
                ))
        );
        try {
            return OBJECT_MAPPER.writeValueAsString(chunk);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot encode stream chunk", exception);
        }
    }
}
