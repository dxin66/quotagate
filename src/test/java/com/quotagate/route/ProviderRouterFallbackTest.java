package com.quotagate.route;

import com.quotagate.api.dto.ChatRequest;
import com.quotagate.api.dto.ChatResponse;
import com.quotagate.provider.LlmProvider;
import com.quotagate.provider.MockProvider;
import com.quotagate.quota.QuotaReservation;
import com.quotagate.quota.QuotaService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

class ProviderRouterFallbackTest {

    private final ChatRequest request = new ChatRequest(
            "gpt-standard",
            List.of(new ChatRequest.Message("user", "hello")),
            false
    );

    @Test
    void fallsBackToTheNextConfiguredProviderIfPrimaryFails() {
        ModelRouteCache routeCache = Mockito.mock(ModelRouteCache.class);
        QuotaService quotaService = Mockito.mock(QuotaService.class);
        ProviderResilience resilience = Mockito.mock(ProviderResilience.class);
        MockProvider mockProvider = new MockProvider();

        Mockito.when(routeCache.findEnabledByAlias("gpt-standard")).thenReturn(List.of(
                new ModelRoute(1L, "gpt-standard", "openai", "gpt-4o-mini", 1, true,
                        BigDecimal.ONE, BigDecimal.ONE),
                new ModelRoute(2L, "gpt-standard", "mock-fast", "mock-fast-model", 2, true,
                        BigDecimal.ONE, BigDecimal.ONE)
        ));

        LlmProvider primaryProvider = request1 -> {
            throw new IllegalStateException("primary provider failed");
        };
        LlmProvider fallbackProvider = request1 -> new ChatResponse(
                "resp-2",
                "chat.completion",
                System.currentTimeMillis(),
                "mock-fast-model",
                List.of(new ChatResponse.Choice(
                        0,
                        new ChatResponse.Message("assistant", "fine"),
                        "stop"
                )),
                new ChatResponse.Usage(10, 5, 15)
        );

        when(quotaService.reserve(anyLong(), Mockito.anyLong())).thenReturn(
                new QuotaReservation(42L, "202609", 128)
        );
        ChatRequest openAiRequest = new ChatRequest(
                "gpt-4o-mini", request.messages(), false);
        ChatRequest mockFastRequest = new ChatRequest(
                "mock-fast-model", request.messages(), false);
        when(resilience.execute("openai", primaryProvider, openAiRequest)).thenThrow(new IllegalStateException("primary provider failed"));
        when(resilience.execute("mock-fast", fallbackProvider, mockFastRequest)).thenReturn(fallbackProvider.chat(mockFastRequest));
        when(quotaService.settleAndPublish(
                Mockito.any(), Mockito.anyLong(), Mockito.any())).thenReturn(true);

        ProviderRouter router = new ProviderRouter(
                routeCache,
                mockProvider,
                resilience,
                Map.of("openai", primaryProvider, "mock-fast", fallbackProvider),
                new SimpleMeterRegistry(),
                quotaService
        );

        ChatResponse response = router.chat(request, 42L);

        assertEquals("resp-2", response.id());
        assertEquals("fine", response.choices().getFirst().message().content());
        Mockito.verify(quotaService).settleAndPublish(
                Mockito.any(), Mockito.eq(15L), Mockito.argThat(event ->
                event.requestId().equals("resp-2")
                        && event.tenantId() == 42L
                        && event.provider().equals("mock-fast")
                        && event.eventType().equals("COMPLETED")));
    }
}
