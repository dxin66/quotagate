package com.quotagate.route;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ModelRouteSingleFlightTest {

    @Autowired private ModelRouteCache cache;

    @Test
    void recordsDatabaseQueriesForConcurrentMisses() throws Exception {
        String alias = "gpt-standard";
        int requests = 500;
        cache.evict(alias);
        long before = cache.dbQueries();

        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<List<ModelRoute>>> futures = new ArrayList<>();

            for (int i = 0; i < requests; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return cache.findEnabledByAlias(alias);
                }));
            }

            boolean allReady = ready.await(10, TimeUnit.SECONDS);
            start.countDown();
            assertTrue(allReady, "500 workers did not become ready");

            for (Future<List<ModelRoute>> future : futures) {
                assertEquals(1, future.get(30, TimeUnit.SECONDS).size());
            }

            long dbQueries = cache.dbQueries() - before;
            System.out.println(
                    "BASELINE: requests=" + requests
                            + ", dbQueries=" + dbQueries
            );
//            assertTrue(dbQueries >= 1);
            assertEquals(1, dbQueries);
        } finally {

            start.countDown();
            cache.evict(alias);
        }
    }
}