package com.quotagate.tenant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class TenantCache {

    private final TenantMapper mapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    private final Cache<Long, Tenant> local = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofSeconds(10))
            .recordStats()
            .build();

    private final AtomicLong redisHits = new AtomicLong();
    private final AtomicLong dbQueries = new AtomicLong();

    public TenantCache(TenantMapper mapper, StringRedisTemplate redis,
                       ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public Tenant findById(long id) {
        Tenant cached = local.getIfPresent(id);
        if (cached != null) {
            return cached;
        }

        String json = redis.opsForValue().get(redisKey(id));
        if (json != null) {
            Tenant tenant = decode(json);
            local.put(id, tenant);
            redisHits.incrementAndGet();
            return tenant;
        }

        dbQueries.incrementAndGet();
        Tenant tenant = mapper.findById(id);
        if (tenant == null) {
            return null;
        }

        long seconds = 60 + ThreadLocalRandom.current().nextInt(31);
        redis.opsForValue().set(
                redisKey(id), encode(tenant), Duration.ofSeconds(seconds));
        local.put(id, tenant);
        return tenant;
    }

    public void evict(long id) {
        local.invalidate(id);
        redis.delete(redisKey(id));
    }

    public long localHits() { return local.stats().hitCount(); }
    public long localMisses() { return local.stats().missCount(); }
    public long redisHits() { return redisHits.get(); }
    public long dbQueries() { return dbQueries.get(); }

    private String encode(Tenant tenant) {
        try {
            return objectMapper.writeValueAsString(tenant);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize tenant", e);
        }
    }

    private Tenant decode(String json) {
        try {
            return objectMapper.readValue(json, Tenant.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid cached tenant", e);
        }
    }

    private static String redisKey(long id) {
        return "qg:tenant:" + id;
    }
}