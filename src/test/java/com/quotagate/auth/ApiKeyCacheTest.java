package com.quotagate.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
class ApiKeyCacheTest {

    @Autowired private ApiKeyCache cache;
    @Autowired private ApiKeyMapper mapper;
    @Autowired private StringRedisTemplate redis;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void readsDatabaseThenLocalThenRedis() {
        String hash = "d9ad46b64ef3a0e2151449ce7db2bb4bf0c7aa815178c519bb251dbe2f088230";
        cache.evict(hash);
        long beforeDb = cache.dbQueries();
        long beforeLocal = cache.localHits();

        try {
            assertNotNull(cache.findByHash(hash));
            assertEquals(beforeDb + 1, cache.dbQueries());

            assertNotNull(cache.findByHash(hash));
            assertEquals(beforeLocal + 1, cache.localHits());

            ApiKeyCache secondInstance =
                    new ApiKeyCache(mapper, redis, objectMapper);
            assertNotNull(secondInstance.findByHash(hash));
            assertEquals(1, secondInstance.redisHits());
            assertEquals(0, secondInstance.dbQueries());
        } finally {
            cache.evict(hash);
        }
    }

    @Test
    void unknownHashIsNegativelyCached() {
        String hash = "0".repeat(64);
        cache.evict(hash);
        long beforeDb = cache.dbQueries();

        try {
            assertNull(cache.findByHash(hash));
            assertEquals(beforeDb + 1, cache.dbQueries());

            assertNull(cache.findByHash(hash));
            assertEquals(beforeDb + 1, cache.dbQueries());

            ApiKeyCache secondInstance =
                    new ApiKeyCache(mapper, redis, objectMapper);
            assertNull(secondInstance.findByHash(hash));
            assertEquals(1, secondInstance.redisHits());
            assertEquals(0, secondInstance.dbQueries());
        } finally {
            cache.evict(hash);
        }
    }
}