package com.quotagate.usage;

import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class UsageProducer {

    public static final String STREAM_KEY = "qg:stream:usage";

    private final StringRedisTemplate redis;

    public UsageProducer(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public String publish(UsageEvent event) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("requestId", event.requestId());
        fields.put("tenantId", Long.toString(event.tenantId()));
        fields.put("provider", event.provider());
        fields.put("inputTokens", Long.toString(event.inputTokens()));
        fields.put("outputTokens", Long.toString(event.outputTokens()));
        fields.put("eventType", event.eventType());

        MapRecord<String, String, String> record =
            StreamRecords.mapBacked(fields).withStreamKey(STREAM_KEY);
        return redis.opsForStream().add(record).getValue();
    }
}