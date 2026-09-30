package com.quotagate.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class TenantCacheTest {

    @Autowired private TenantCache cache;
    @Autowired private TenantMapper mapper;
    @Autowired private StringRedisTemplate redis;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void readsDatabaseThenLocalThenRedis() {
        cache.evict(1L);
        long beforeDb = cache.dbQueries();
        long beforeLocal = cache.localHits();

        try {
            assertNotNull(cache.findById(1L));
            assertEquals(beforeDb + 1, cache.dbQueries());

            assertNotNull(cache.findById(1L));
            assertEquals(beforeLocal + 1, cache.localHits());

            TenantCache secondInstance =
                    new TenantCache(mapper, redis, objectMapper);
            assertEquals("ACTIVE", secondInstance.findById(1L).status());
            assertEquals(1, secondInstance.redisHits());
            assertEquals(0, secondInstance.dbQueries());
        } finally {
            cache.evict(1L);
        }
    }
}