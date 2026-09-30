package com.quotagate.quota;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import com.quotagate.usage.UsageEvent;
import com.quotagate.usage.UsageProducer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class QuotaServiceTest {

    private static final long TENANT_ID = 991001L;

    @Autowired private QuotaService quotaService;
    @Autowired private StringRedisTemplate redis;

    @Test
    void reservesQuotaAtomicallyUnderConcurrency() throws Exception {
        long limit = 100_000;
        long estimate = 101;
        int requests = 1_000;
        String key = quotaKey();
        redis.delete(key);
        quotaService.configureLimit(TENANT_ID, limit);

        List<QuotaReservation> reservations = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<java.util.concurrent.Future<QuotaReservation>>();
            for (int i = 0; i < requests; i++) {
                futures.add(executor.submit(
                        () -> quotaService.reserve(TENANT_ID, estimate)));
            }

            for (var future : futures) {
                QuotaReservation reservation = future.get();
                if (reservation != null) {
                    reservations.add(reservation);
                }
            }
        }

        try {
            long expectedAccepted = limit / estimate;
            assertEquals(expectedAccepted, reservations.size());
            assertEquals(expectedAccepted * estimate,
                    quotaService.reserved(TENANT_ID));
            assertEquals(0, quotaService.used(TENANT_ID));
            assertTrue(quotaService.used(TENANT_ID)
                    + quotaService.reserved(TENANT_ID)
                    <= quotaService.limit(TENANT_ID));

            for (QuotaReservation reservation : reservations) {
                assertTrue(quotaService.settle(reservation, 60));
            }

            assertEquals(0, quotaService.reserved(TENANT_ID));
            assertEquals(expectedAccepted * 60,
                    quotaService.used(TENANT_ID));
        } finally {
            redis.delete(key);
        }
    }

    @Test
    void refundReleasesReservationWithoutChargingUsage() {
        String key = quotaKey();
        redis.delete(key);
        quotaService.configureLimit(TENANT_ID, 1_000);

        try {
            QuotaReservation reservation = quotaService.reserve(TENANT_ID, 400);
            assertTrue(reservation != null);
            assertTrue(quotaService.refund(reservation));
            assertEquals(0, quotaService.reserved(TENANT_ID));
            assertEquals(0, quotaService.used(TENANT_ID));
        } finally {
            redis.delete(key);
        }
    }

    @Test
    void settlesQuotaAndPublishesUsageAtomically() {
        String key = quotaKey();
        redis.delete(key);
        redis.delete(UsageProducer.STREAM_KEY);
        quotaService.configureLimit(TENANT_ID, 1_000);

        try {
            QuotaReservation reservation = quotaService.reserve(TENANT_ID, 400);
            UsageEvent event = new UsageEvent(
                    "atomic-accounting-test",
                    TENANT_ID,
                    "mock",
                    100,
                    50,
                    "COMPLETED"
            );

            assertTrue(quotaService.settleAndPublish(reservation, 150, event));
            assertEquals(150, quotaService.used(TENANT_ID));
            assertEquals(0, quotaService.reserved(TENANT_ID));
            assertEquals(1, redis.opsForStream().size(UsageProducer.STREAM_KEY));
        } finally {
            redis.delete(key);
            redis.delete(UsageProducer.STREAM_KEY);
        }
    }

    private String quotaKey() {
        return "qg:quota:" + TENANT_ID + ":"
                + YearMonth.now(java.time.Clock.systemUTC())
                .toString().replace("-", "");
    }
}
