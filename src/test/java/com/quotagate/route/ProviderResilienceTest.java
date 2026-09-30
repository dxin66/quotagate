package com.quotagate.route;

import com.quotagate.api.dto.ChatRequest;
import com.quotagate.provider.MockFailureProvider;
import com.quotagate.provider.MockSlowProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderResilienceTest {

    private final ChatRequest request = new ChatRequest(
            "gpt-standard",
            List.of(new ChatRequest.Message("user", "hello")),
            false
    );

    @Test
    void timesOutSlowProvider() {
        ProviderResilience resilience =
                new ProviderResilience(new SimpleMeterRegistry());
        long started = System.nanoTime();

        try {
            assertThrows(RuntimeException.class, () -> resilience.execute(
                    "mock-slow",
                    new MockSlowProvider(),
                    request
            ));
            long elapsedMillis = Duration.ofNanos(
                    System.nanoTime() - started).toMillis();
            assertTrue(elapsedMillis < 1_500,
                    "timeout should return before the mock's 2 second delay");
        } finally {
            resilience.shutdown();
        }
    }

    @Test
    void opensCircuitAfterRepeatedProviderFailures() {
        ProviderResilience resilience =
                new ProviderResilience(new SimpleMeterRegistry());

        try {
            for (int i = 0; i < 3; i++) {
                assertThrows(RuntimeException.class, () -> resilience.execute(
                        "mock-failure",
                        new MockFailureProvider(),
                        request
                ));
            }
            assertTrue(resilience.isCircuitOpen("mock-failure"));
        } finally {
            resilience.shutdown();
        }
    }
}