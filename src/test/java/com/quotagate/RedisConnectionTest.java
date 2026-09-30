package com.quotagate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class RedisConnectionTest {

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void readsAndWritesRedis() {
        String key = "qg:test:connection";
        try {
            redis.opsForValue().set(key, "ok", Duration.ofSeconds(10));
            assertEquals("ok", redis.opsForValue().get(key));
        } finally {
            redis.delete(key);
        }
    }
}