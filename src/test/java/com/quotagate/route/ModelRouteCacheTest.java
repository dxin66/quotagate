package com.quotagate.route;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ModelRouteCacheTest {

    @Autowired private ModelRouteCache cache;
    @Autowired private ModelRouteMapper mapper;
    @Autowired private StringRedisTemplate redis;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void readsDatabaseThenLocalThenRedis() {
        String alias = "gpt-standard";
        cache.evict(alias);
        long beforeDb = cache.dbQueries();
        long beforeLocal = cache.localHits();

        try {
            assertEquals("mock",
                    cache.findEnabledByAlias(alias).getFirst().provider());
            assertEquals(beforeDb + 1, cache.dbQueries());

            assertEquals("mock",
                    cache.findEnabledByAlias(alias).getFirst().provider());
            assertEquals(beforeLocal + 1, cache.localHits());

            ModelRouteCache secondInstance =
//                    Runnable::run让测试中的刷新任务同步执行，避免额外创建线程池
                    new ModelRouteCache(mapper, redis, objectMapper,Runnable::run);
            assertEquals("mock",
                    secondInstance.findEnabledByAlias(alias)
                            .getFirst().provider());
            assertEquals(1, secondInstance.redisHits());
            assertEquals(0, secondInstance.dbQueries());
        } finally {
            cache.evict(alias);
        }
    }

        @Test
        void servesStaleRoutesAndRefreshesRedis() throws Exception {
        String alias = "gpt-standard";
        ModelRoute staleRoute = new ModelRoute(
            99L,
            alias,
            "stale-provider",
            "stale-model",
            1,
            true,
            java.math.BigDecimal.ZERO,
            java.math.BigDecimal.ZERO
        );
        CachedModelRoutes stale = new CachedModelRoutes(
            List.of(staleRoute),
            System.currentTimeMillis() - 1
        );

        cache.evict(alias);
        redis.opsForValue().set(
            "qg:model:" + alias,
            objectMapper.writeValueAsString(stale),
            Duration.ofMinutes(5)
        );

        try {
            ModelRouteCache synchronousRefresh =
                new ModelRouteCache(mapper, redis, objectMapper, Runnable::run);

            assertEquals("stale-provider",
                synchronousRefresh.findEnabledByAlias(alias)
                    .getFirst().provider());

            CachedModelRoutes refreshed = objectMapper.readValue(
                redis.opsForValue().get("qg:model:" + alias),
                CachedModelRoutes.class
            );
            assertEquals("mock", refreshed.routes().getFirst().provider());
            assertTrue(refreshed.expiresAtEpochMilli()
                > System.currentTimeMillis());
        } finally {
            cache.evict(alias);
        }
        }
}