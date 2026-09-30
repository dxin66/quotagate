package com.quotagate.usage;

import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class UsageConsumer {

    public static final String GROUP = "billing-consumer";

    private final StringRedisTemplate redis;
    private final UsageLedgerMapper ledgerMapper;

    public UsageConsumer(
            StringRedisTemplate redis,
            UsageLedgerMapper ledgerMapper
    ) {
        this.redis = redis;
        this.ledgerMapper = ledgerMapper;
    }

    public void ensureConsumerGroup() {
        StreamOperations<String, String, String> streams = redis.opsForStream();
        try {
            if (!Boolean.TRUE.equals(redis.hasKey(UsageProducer.STREAM_KEY))) {
                streams.add(UsageProducer.STREAM_KEY, java.util.Map.of(
                        "requestId", "bootstrap-" + UUID.randomUUID(),
                        "tenantId", "0",
                        "provider", "system",
                        "inputTokens", "0",
                        "outputTokens", "0",
                        "eventType", "BOOTSTRAP"
                ));
            }
            streams.createGroup(
                    UsageProducer.STREAM_KEY,
                    ReadOffset.latest(),
                    GROUP
            );
        } catch (org.springframework.data.redis.RedisSystemException exception) {
            if (!isBusyGroup(exception)) {
                throw exception;
            }
        }
    }

    public int consumeOnce(String consumerName, int count) {
        ensureConsumerGroup();
        List<MapRecord<String, Object, Object>> records = read(
                consumerName, count, ReadOffset.from("0"));
        if (records == null || records.isEmpty()) {
            records = read(consumerName, count, ReadOffset.lastConsumed());
        }

        if (records == null || records.isEmpty()) {
            return 0;
        }

        persistAndAck(records);
        return records.size();
    }

    private List<MapRecord<String, Object, Object>> read(
            String consumerName,
            int count,
            ReadOffset offset
    ) {
        return redis.opsForStream().read(
                Consumer.from(GROUP, consumerName),
                StreamReadOptions.empty().count(count),
                StreamOffset.create(UsageProducer.STREAM_KEY, offset)
        );
    }

    private void persistAndAck(List<MapRecord<String, Object, Object>> records) {
        ledgerMapper.insertBatch(records.stream().map(this::toEvent).toList());
        RecordId[] recordIds = records.stream()
                .map(MapRecord::getId)
                .toArray(RecordId[]::new);
        redis.opsForStream().acknowledge(
                UsageProducer.STREAM_KEY,
                GROUP,
                recordIds
        );
        redis.opsForStream().trim(UsageProducer.STREAM_KEY, 100_000, true);
    }

    private UsageEvent toEvent(MapRecord<String, Object, Object> record) {
        var values = record.getValue();
        return new UsageEvent(
                values.get("requestId").toString(),
                Long.parseLong(values.get("tenantId").toString()),
                values.get("provider").toString(),
                Long.parseLong(values.get("inputTokens").toString()),
                Long.parseLong(values.get("outputTokens").toString()),
                values.get("eventType").toString()
        );
    }

    private boolean isBusyGroup(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current.getMessage() != null
                    && current.getMessage().contains("BUSYGROUP")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
