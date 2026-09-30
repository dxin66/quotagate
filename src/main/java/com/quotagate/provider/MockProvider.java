package com.quotagate.provider;

import com.quotagate.api.dto.ChatRequest;
import com.quotagate.api.dto.ChatResponse;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component("mock")
public class MockProvider implements LlmProvider {

    @Override
    public ChatResponse chat(ChatRequest request) {
        return MockResponseFactory.create(request, "mock");
    }
}
