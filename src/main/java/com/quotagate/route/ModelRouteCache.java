package com.quotagate.route;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ModelRouteCache {

    private static final Duration LOGICAL_TTL = Duration.ofSeconds(30);

    private final ModelRouteMapper mapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Executor refreshExecutor;

    private final Set<String> refreshing =
            ConcurrentHashMap.newKeySet();

    private final Cache<String, CachedModelRoutes> local =
            Caffeine.newBuilder()
                    .maximumSize(10_000)
                    .expireAfterWrite(Duration.ofMinutes(10))
                    .recordStats()
                    .build();

    private final AtomicLong redisHits = new AtomicLong();
    private final AtomicLong dbQueries = new AtomicLong();

    public ModelRouteCache(
            ModelRouteMapper mapper,
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            @Qualifier("routeRefreshExecutor") Executor refreshExecutor
    ) {
        this.mapper = mapper;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.refreshExecutor = refreshExecutor;
    }

    public List<ModelRoute> findEnabledByAlias(String alias) {
        CachedModelRoutes cached = local.get(alias, this::load);

        if (cached == null) {
            return List.of();
        }

        if (cached.isExpired(System.currentTimeMillis())) {
            refreshAsync(alias);
        }

        return cached.routes();
    }

    private CachedModelRoutes load(String alias) {
        String json = redis.opsForValue().get(redisKey(alias));

        if (json != null) {
            redisHits.incrementAndGet();
            return decode(json);
        }

        return loadFromDatabase(alias);
    }

    private CachedModelRoutes loadFromDatabase(String alias) {
        dbQueries.incrementAndGet();

        List<ModelRoute> routes =
                mapper.findEnabledByAlias(alias);

        if (routes.isEmpty()) {
            return null;
        }

        CachedModelRoutes cached = new CachedModelRoutes(
                routes,
                System.currentTimeMillis() + LOGICAL_TTL.toMillis()
        );

        writeRedis(alias, cached);

        return cached;
    }

        private void writeRedis(String alias, CachedModelRoutes cached) {
        long physicalSeconds =
            300 + ThreadLocalRandom.current().nextInt(61);

        redis.opsForValue().set(
            redisKey(alias),
            encode(cached),
            Duration.ofSeconds(physicalSeconds)
        );
        }

    private void refreshAsync(String alias) {
//        refreshing.add(alias) 是刷新层的 Single Flight。同一个模型正在刷新时，其他请求不会再提交刷新任务。
//        Redis 物理 TTL 设为 300–360 秒，逻辑 TTL 为 30 秒。物理 TTL 更长，才能在逻辑过期后继续提供旧数据。
        if (!refreshing.add(alias)) {
            return;
        }

        refreshExecutor.execute(() -> {
            try {
                CachedModelRoutes refreshed =
                        loadFromDatabase(alias);

                if (refreshed != null) {
                    local.put(alias, refreshed);
                }
            } finally {
                refreshing.remove(alias);
            }
        });
    }

    public void evict(String alias) {
        local.invalidate(alias);
        redis.delete(redisKey(alias));
    }

    public long localHits() {
        return local.stats().hitCount();
    }

    public long localMisses() {
        return local.stats().missCount();
    }

    public long redisHits() {
        return redisHits.get();
    }

    public long dbQueries() {
        return dbQueries.get();
    }

    private String encode(CachedModelRoutes cached) {
        try {
            return objectMapper.writeValueAsString(cached);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Cannot serialize model routes", e);
        }
    }

    private CachedModelRoutes decode(String json) {
        try {
            return objectMapper.readValue(
                    json, CachedModelRoutes.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Invalid cached model routes", e);
        }
    }

    private static String redisKey(String alias) {
        return "qg:model:" + alias;
    }
}