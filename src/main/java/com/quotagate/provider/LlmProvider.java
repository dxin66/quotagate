package com.quotagate.provider;

import com.quotagate.api.dto.ChatRequest;
import com.quotagate.api.dto.ChatResponse;

import java.util.function.Consumer;

public interface LlmProvider {
    ChatResponse chat(ChatRequest request);

    default ProviderStreamResult stream(
            ChatRequest request,
            Consumer<String> chunkConsumer
    ) {
        ChatResponse response = chat(new ChatRequest(
                request.model(), request.messages(), false));
        chunkConsumer.accept(StreamChunkEncoder.encode(response));
        return new ProviderStreamResult(response.id(), response.usage());
    }
}
