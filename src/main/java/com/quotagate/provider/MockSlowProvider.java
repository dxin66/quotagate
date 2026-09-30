package com.quotagate.provider;

import com.quotagate.api.dto.ChatRequest;
import com.quotagate.api.dto.ChatResponse;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component("mock-slow")
public class MockSlowProvider implements LlmProvider {

    @Override
    public ChatResponse chat(ChatRequest request) {
        try {
            Thread.sleep(Duration.ofSeconds(2));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Slow provider interrupted", exception);
        }
        return MockResponseFactory.create(request, "mock-slow");
    }
}