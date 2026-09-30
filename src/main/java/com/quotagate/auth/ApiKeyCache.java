package com.quotagate.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ApiKeyCache {

    private final ApiKeyMapper mapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    private final Cache<String, Optional<ApiKey>> local = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofSeconds(10))
            .recordStats()
            .build();

    private final AtomicLong redisHits = new AtomicLong();
    private final AtomicLong dbQueries = new AtomicLong();

    public ApiKeyCache(ApiKeyMapper mapper, StringRedisTemplate redis,
                       ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public ApiKey findByHash(String hash) {
        Optional<ApiKey> localValue = local.getIfPresent(hash);
        if (localValue != null) {
            return localValue.orElse(null);
        }

        String json = redis.opsForValue().get(positiveKey(hash));
        if (json != null) {
            ApiKey value = decode(json);
            local.put(hash, Optional.of(value));
            redisHits.incrementAndGet();
            return value;
        }

        if (redis.opsForValue().get(negativeKey(hash)) != null) {
            local.put(hash, Optional.empty());
            redisHits.incrementAndGet();
            return null;
        }

        dbQueries.incrementAndGet();
        ApiKey value = mapper.findByHash(hash);

        if (value == null) {
            long seconds = 30 + ThreadLocalRandom.current().nextInt(31);
            redis.opsForValue().set(
                    negativeKey(hash), "1", Duration.ofSeconds(seconds));
            local.put(hash, Optional.empty());
            return null;
        }

        long seconds = 60 + ThreadLocalRandom.current().nextInt(31);
        redis.opsForValue().set(
                positiveKey(hash), encode(value), Duration.ofSeconds(seconds));
        local.put(hash, Optional.of(value));
        return value;
    }

    public void evict(String hash) {
        local.invalidate(hash);
        redis.delete(List.of(positiveKey(hash), negativeKey(hash)));
    }

    public long localHits() { return local.stats().hitCount(); }
    public long localMisses() { return local.stats().missCount(); }
    public long redisHits() { return redisHits.get(); }
    public long dbQueries() { return dbQueries.get(); }

    private String encode(ApiKey value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize API key", e);
        }
    }

    private ApiKey decode(String json) {
        try {
            return objectMapper.readValue(json, ApiKey.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid cached API key", e);
        }
    }

    private static String positiveKey(String hash) {
        return "qg:apikey:" + hash;
    }

    private static String negativeKey(String hash) {
        return "qg:apikey:null:" + hash;
    }
}