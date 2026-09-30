package com.quotagate.quota;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import com.quotagate.usage.UsageEvent;
import com.quotagate.usage.UsageProducer;

import java.io.IOException;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;

@Service
public class QuotaService {

    private static final DefaultRedisScript<Long> RESERVE_SCRIPT =
            new DefaultRedisScript<Long>(
                scriptText("scripts/quota/reserve.lua"),
                    Long.class
            );

    private static final DefaultRedisScript<Long> SETTLE_SCRIPT =
            new DefaultRedisScript<Long>(
                scriptText("scripts/quota/settle.lua"),
                    Long.class
            );

    private static final DefaultRedisScript<Long> SETTLE_AND_PUBLISH_SCRIPT =
            new DefaultRedisScript<Long>(
                    scriptText("scripts/quota/settle_and_publish.lua"),
                    Long.class
            );

    private final RedisTemplate<String, String> redis;
    private final Clock clock;

    @Autowired
    public QuotaService(RedisTemplate<String, String> redis) {
        this(redis, Clock.systemUTC());
    }

    QuotaService(RedisTemplate<String, String> redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    public void configureLimit(long tenantId, long limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("Quota limit cannot be negative");
        }

        redis.opsForHash().put(quotaKey(tenantId), "limit", Long.toString(limit));
        redis.opsForHash().putIfAbsent(quotaKey(tenantId), "used", "0");
        redis.opsForHash().putIfAbsent(quotaKey(tenantId), "reserved", "0");
    }

    public void ensureLimit(long tenantId, long defaultLimit) {
        if (defaultLimit < 0) {
            throw new IllegalArgumentException("Quota limit cannot be negative");
        }

        String key = quotaKey(tenantId);
        redis.opsForHash().putIfAbsent(key, "limit", Long.toString(defaultLimit));
        redis.opsForHash().putIfAbsent(key, "used", "0");
        redis.opsForHash().putIfAbsent(key, "reserved", "0");
    }

    public QuotaReservation reserve(long tenantId, long estimatedTokens) {
        String month = currentMonth();
        Long result = redis.execute(
                RESERVE_SCRIPT,
                List.of(quotaKey(tenantId, month)),
                Long.toString(estimatedTokens)
        );

        if (Long.valueOf(1L).equals(result)) {
            return new QuotaReservation(tenantId, month, estimatedTokens);
        }

        return null;
    }

    public boolean settle(QuotaReservation reservation, long actualTokens) {
        return executeSettlement(reservation, actualTokens);
    }

    public boolean refund(QuotaReservation reservation) {
        return executeSettlement(reservation, 0);
    }

    public boolean settleAndPublish(
            QuotaReservation reservation,
            long actualTokens,
            UsageEvent event
    ) {
        Long result = redis.execute(
                SETTLE_AND_PUBLISH_SCRIPT,
                List.of(
                        quotaKey(reservation.tenantId(), reservation.month()),
                        UsageProducer.STREAM_KEY
                ),
                Long.toString(reservation.estimatedTokens()),
                Long.toString(actualTokens),
                event.requestId(),
                Long.toString(event.tenantId()),
                event.provider(),
                Long.toString(event.inputTokens()),
                Long.toString(event.outputTokens()),
                event.eventType()
        );
        return Long.valueOf(1L).equals(result);
    }

    public long used(long tenantId) {
        return value(tenantId, "used");
    }

    public long reserved(long tenantId) {
        return value(tenantId, "reserved");
    }

    public long limit(long tenantId) {
        return value(tenantId, "limit");
    }

    private boolean executeSettlement(
            QuotaReservation reservation,
            long actualTokens
    ) {
        Long result = redis.execute(
                SETTLE_SCRIPT,
                List.of(quotaKey(reservation.tenantId(), reservation.month())),
                Long.toString(reservation.estimatedTokens()),
                Long.toString(actualTokens)
        );
        return Long.valueOf(1L).equals(result);
    }

    private long value(long tenantId, String field) {
        Object value = redis.opsForHash().get(quotaKey(tenantId), field);
        return value == null ? 0 : Long.parseLong(value.toString());
    }

    private String currentMonth() {
        return YearMonth.now(clock).toString().replace("-", "");
    }

    private String quotaKey(long tenantId) {
        return quotaKey(tenantId, currentMonth());
    }

    private String quotaKey(long tenantId, String month) {
        return "qg:quota:" + tenantId + ":" + month;
    }

    private static String scriptText(String path) {
        try (var input = new ClassPathResource(path).getInputStream()) {
            return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load Redis script: " + path, exception);
        }
    }
}
