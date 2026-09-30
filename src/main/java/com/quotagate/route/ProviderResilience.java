package com.quotagate.route;

import com.quotagate.api.dto.ChatRequest;
import com.quotagate.api.dto.ChatResponse;
import com.quotagate.provider.LlmProvider;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

@Service
public class ProviderResilience {

    private final Map<String, CircuitBreaker> circuitBreakers =
            new ConcurrentHashMap<>();
    private final Map<String, Retry> retries = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;
    private final Duration timeout;
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(4);

    @Autowired
    public ProviderResilience(
            MeterRegistry meterRegistry,
            @Value("${quotagate.resilience.timeout-ms:30000}") long timeoutMillis
    ) {
        this.meterRegistry = meterRegistry;
        this.timeout = Duration.ofMillis(timeoutMillis);
    }

    public ProviderResilience(MeterRegistry meterRegistry) {
        this(meterRegistry, 500);
    }

    public ChatResponse execute(
            String providerName,
            LlmProvider provider,
            ChatRequest request
    ) {
        CircuitBreaker circuitBreaker = circuitBreakers.computeIfAbsent(
                providerName,
                name -> CircuitBreaker.of(name, circuitBreakerConfig())
        );
        Retry retry = retries.computeIfAbsent(
                providerName,
                name -> Retry.of(name, retryConfig())
        );
        TimeLimiter timeLimiter = TimeLimiter.of(timeout);

        Supplier<CompletionStage<ChatResponse>> asyncCall = () ->
                CompletableFuture.supplyAsync(() -> provider.chat(request));
        Supplier<CompletionStage<ChatResponse>> timed = () ->
            timeLimiter.executeCompletionStage(scheduler, asyncCall);
        Supplier<CompletionStage<ChatResponse>> retried = retry
            .decorateCompletionStage(scheduler, timed);
        Supplier<CompletionStage<ChatResponse>> decorated = circuitBreaker
            .decorateCompletionStage(retried);

        try {
            ChatResponse response = decorated.get().toCompletableFuture().join();
            Counter.builder("provider_requests_total")
                    .tag("provider", providerName)
                    .tag("outcome", "success")
                    .register(meterRegistry)
                    .increment();
            return response;
        } catch (RuntimeException exception) {
            Counter.builder("provider_requests_total")
                    .tag("provider", providerName)
                    .tag("outcome", "failure")
                    .register(meterRegistry)
                    .increment();
            throw unwrap(exception);
        }
    }

    public boolean isCircuitOpen(String providerName) {
        CircuitBreaker breaker = circuitBreakers.get(providerName);
        return breaker != null
                && breaker.getState() == CircuitBreaker.State.OPEN;
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }

    private RuntimeException unwrap(RuntimeException exception) {
        Throwable cause = exception.getCause();
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        if (cause instanceof TimeoutException timeoutException) {
            return new IllegalStateException("Provider timed out", timeoutException);
        }
        return exception;
    }

    private CircuitBreakerConfig circuitBreakerConfig() {
        return CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(3)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .build();
    }

    private RetryConfig retryConfig() {
        return RetryConfig.custom()
                .maxAttempts(2)
                .waitDuration(Duration.ofMillis(20))
                .build();
    }
}
