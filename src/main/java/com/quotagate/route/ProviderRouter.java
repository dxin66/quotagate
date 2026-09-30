package com.quotagate.route;

import com.quotagate.api.dto.ChatRequest;
import com.quotagate.api.dto.ChatResponse;
import com.quotagate.provider.LlmProvider;
import com.quotagate.provider.MockProvider;
import com.quotagate.provider.ProviderStreamResult;
import com.quotagate.quota.QuotaReservation;
import com.quotagate.quota.QuotaService;
import com.quotagate.usage.UsageEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Service
public class ProviderRouter {

    private final ModelRouteCache routeCache;
    private final MockProvider mockProvider;
    private final ProviderResilience resilience;
    private final Map<String, LlmProvider> providers;
    private final MeterRegistry meterRegistry;
    private final QuotaService quotaService;

    private static final long DEFAULT_MONTHLY_QUOTA = 1_000_000;

    public ProviderRouter(
            ModelRouteCache routeCache,
            MockProvider mockProvider,
            ProviderResilience resilience,
            Map<String, LlmProvider> providers,
            MeterRegistry meterRegistry,
            QuotaService quotaService
    ) {
        this.routeCache = routeCache;
        this.mockProvider = mockProvider;
        this.resilience = resilience;
        this.providers = providers;
        this.meterRegistry = meterRegistry;
        this.quotaService = quotaService;
    }

    public ChatResponse chat(ChatRequest request, long tenantId) {
        List<ModelRoute> routes =
                routeCache.findEnabledByAlias(request.model());

        if (routes.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Model route not found: " + request.model()
            );
        }

        long estimatedTokens = estimateTokens(request);
        quotaService.ensureLimit(tenantId, DEFAULT_MONTHLY_QUOTA);
        QuotaReservation reservation =
                quotaService.reserve(tenantId, estimatedTokens);
        if (reservation == null) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Monthly token quota exceeded"
            );
        }

        RuntimeException lastException = null;
        for (ModelRoute route : routes) {
            LlmProvider provider = providers.get(route.provider());
            if (provider == null) {
                continue;
            }

            ChatResponse response;
            try {
                ChatRequest upstreamRequest = new ChatRequest(
                        route.upstreamModel(), request.messages(), false);
                response = resilience.execute(
                        route.provider(), provider, upstreamRequest);
            } catch (RuntimeException exception) {
                lastException = exception;
                Counter.builder("provider_fallback_total")
                        .tag("provider", route.provider())
                        .register(meterRegistry)
                        .increment();
                continue;
            }
            completeUsage(tenantId, reservation, route.provider(), response);
            return response;
        }

        ChatResponse fallbackResponse;
        try {
            fallbackResponse = mockProvider.chat(request);
        } catch (RuntimeException fallbackException) {
            quotaService.refund(reservation);
            if (lastException != null) {
                throw lastException;
            }
            throw fallbackException;
        }
        completeUsage(tenantId, reservation, "mock", fallbackResponse);
        return fallbackResponse;
    }

    public void stream(
            ChatRequest request,
            long tenantId,
            Consumer<String> chunkConsumer
    ) {
        List<ModelRoute> routes = routeCache.findEnabledByAlias(request.model());
        if (routes.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Model route not found: " + request.model());
        }

        long estimatedTokens = estimateTokens(request);
        quotaService.ensureLimit(tenantId, DEFAULT_MONTHLY_QUOTA);
        QuotaReservation reservation = quotaService.reserve(tenantId, estimatedTokens);
        if (reservation == null) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Monthly token quota exceeded");
        }

        RuntimeException lastException = null;
        for (ModelRoute route : routes) {
            LlmProvider provider = providers.get(route.provider());
            if (provider == null) {
                continue;
            }
            AtomicBoolean emitted = new AtomicBoolean();
            ProviderStreamResult result;
            try {
                ChatRequest upstreamRequest = new ChatRequest(
                        route.upstreamModel(), request.messages(), true);
                result = provider.stream(
                        upstreamRequest,
                        chunk -> {
                            emitted.set(true);
                            chunkConsumer.accept(chunk);
                        });
            } catch (RuntimeException exception) {
                lastException = exception;
                if (emitted.get()) {
                    recordPartialUsage(
                            tenantId, reservation, route.provider(), estimatedTokens);
                    throw exception;
                }
                continue;
            }
            completeUsage(
                    tenantId, reservation, route.provider(),
                    result.requestId(), result.usage());
            return;
        }

        ProviderStreamResult fallbackResult;
        try {
            fallbackResult = mockProvider.stream(request, chunkConsumer);
        } catch (RuntimeException fallbackException) {
            quotaService.refund(reservation);
            if (lastException != null) {
                throw lastException;
            }
            throw fallbackException;
        }
        completeUsage(
                tenantId, reservation, "mock",
                fallbackResult.requestId(), fallbackResult.usage());
    }

    private void completeUsage(
            long tenantId,
            QuotaReservation reservation,
            String provider,
            ChatResponse response
    ) {
        completeUsage(
                tenantId, reservation, provider,
                response.id(), response.usage());
    }

    private void completeUsage(
            long tenantId,
            QuotaReservation reservation,
            String provider,
            String requestId,
            ChatResponse.Usage usage
    ) {
        UsageEvent event = new UsageEvent(
                requestId,
                tenantId,
                provider,
                usage.promptTokens(),
                usage.completionTokens(),
                "COMPLETED"
        );
        if (!quotaService.settleAndPublish(
                reservation, usage.totalTokens(), event)) {
            throw new IllegalStateException("Token quota settlement failed");
        }
    }

    private void recordPartialUsage(
            long tenantId,
            QuotaReservation reservation,
            String provider,
            long estimatedTokens
    ) {
        UsageEvent event = new UsageEvent(
                "partial-" + UUID.randomUUID(),
                tenantId,
                provider,
                estimatedTokens,
                0,
                "PARTIAL"
        );
        if (!quotaService.settleAndPublish(
                reservation, estimatedTokens, event)) {
            throw new IllegalStateException("Partial token settlement failed");
        }
    }

    private long estimateTokens(ChatRequest request) {
        long inputCharacters = request.messages().stream()
                .mapToLong(message -> message.content().length())
                .sum();
        return Math.max(1, inputCharacters + 256);
    }
}
