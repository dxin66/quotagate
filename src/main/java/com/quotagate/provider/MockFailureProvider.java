package com.quotagate.provider;

import com.quotagate.api.dto.ChatRequest;
import com.quotagate.api.dto.ChatResponse;
import org.springframework.stereotype.Component;

@Component("mock-failure")
public class MockFailureProvider implements LlmProvider {

    @Override
    public ChatResponse chat(ChatRequest request) {
        throw new IllegalStateException("Mock provider failure");
    }
}