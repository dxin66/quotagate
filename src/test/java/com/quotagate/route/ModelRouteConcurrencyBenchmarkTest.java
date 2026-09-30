package com.quotagate.route;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ModelRouteConcurrencyBenchmarkTest {

    @Autowired private ModelRouteMapper mapper;
    @Autowired private ModelRouteCache cache;

    @Test
    void comparesCacheAsideWithLogicalExpireSingleFlight() throws Exception {
        String alias = "gpt-standard";
        int requests = 500;

        BenchmarkResult baseline = runConcurrent(requests,
                () -> mapper.findEnabledByAlias(alias));

        cache.evict(alias);
        long beforeDbQueries = cache.dbQueries();
        BenchmarkResult optimized = runConcurrent(requests,
                () -> cache.findEnabledByAlias(alias));
        long optimizedDbQueries = cache.dbQueries() - beforeDbQueries;

        try {
            assertEquals(requests, baseline.completedRequests());
            assertEquals(requests, optimized.completedRequests());
            assertEquals(1, optimizedDbQueries);
            assertEquals(1, optimized.routeCount());

            System.out.printf(
                    "DAY3: requests=%d, baselineDbQueries=%d, "
                            + "optimizedDbQueries=%d, baselineP95Ms=%.2f, "
                            + "optimizedP95Ms=%.2f, localHits=%d, localMisses=%d%n",
                    requests,
                    baseline.completedRequests(),
                    optimizedDbQueries,
                    baseline.p95Millis(),
                    optimized.p95Millis(),
                    cache.localHits(),
                    cache.localMisses()
            );
        } finally {
            cache.evict(alias);
        }
    }

    private BenchmarkResult runConcurrent(
            int requests,
            RouteLookup lookup
    ) throws Exception {
        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch start = new CountDownLatch(1);
        List<Long> durationsNanos = Collections.synchronizedList(
                new ArrayList<>(requests));
        List<Future<List<ModelRoute>>> futures = new ArrayList<>(requests);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < requests; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException(
                                "Benchmark workers did not start");
                    }

                    long started = System.nanoTime();
                    List<ModelRoute> routes = lookup.find();
                    durationsNanos.add(System.nanoTime() - started);
                    return routes;
                }));
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS),
                    "Benchmark workers did not become ready");
            start.countDown();

            int routeCount = 0;
            for (Future<List<ModelRoute>> future : futures) {
                List<ModelRoute> routes = future.get(30, TimeUnit.SECONDS);
                assertEquals(1, routes.size());
                routeCount = routes.size();
            }

            return new BenchmarkResult(
                    requests,
                    routeCount,
                    percentileMillis(durationsNanos, 0.95)
            );
        } finally {
            start.countDown();
        }
    }

    private double percentileMillis(
            List<Long> durationsNanos,
            double percentile
    ) {
        List<Long> sorted = new ArrayList<>(durationsNanos);
        Collections.sort(sorted);
        int index = (int) Math.ceil(sorted.size() * percentile) - 1;
        return sorted.get(Math.max(0, index)) / 1_000_000.0;
    }

    @FunctionalInterface
    private interface RouteLookup {
        List<ModelRoute> find();
    }

    private record BenchmarkResult(
            int completedRequests,
            int routeCount,
            double p95Millis
    ) {
    }
}