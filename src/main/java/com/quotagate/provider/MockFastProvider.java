package com.quotagate.provider;

import com.quotagate.api.dto.ChatRequest;
import com.quotagate.api.dto.ChatResponse;
import org.springframework.stereotype.Component;

@Component("mock-fast")
public class MockFastProvider implements LlmProvider {

    @Override
    public ChatResponse chat(ChatRequest request) {
        return MockResponseFactory.create(request, "mock-fast");
    }
}