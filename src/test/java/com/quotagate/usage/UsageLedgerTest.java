package com.quotagate.usage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.UUID;

@SpringBootTest
class UsageLedgerTest {

    @Autowired private StringRedisTemplate redis;
    @Autowired private UsageProducer producer;
    @Autowired private UsageConsumer consumer;
    @Autowired private UsageLedgerMapper ledgerMapper;

    @Test
    void duplicateUsageEventsAreIdempotent() {
        UsageEvent event = new UsageEvent(
                "request-ledger-test-" + UUID.randomUUID(),
                7L,
                "mock",
                12L,
                8L,
                "COMPLETED"
        );

        redis.delete(UsageProducer.STREAM_KEY);
        producer.publish(new UsageEvent(
                "stream-bootstrap",
                7L,
                "mock",
                0L,
                0L,
                "BOOTSTRAP"
        ));
        consumer.ensureConsumerGroup();
        producer.publish(event);
        producer.publish(event);

        try {
            assertEquals(2, consumer.consumeOnce("test-consumer", 10));
            assertEquals(1, ledgerMapper.countByRequestAndEvent(
                    event.requestId(), event.eventType()));
        } finally {
            redis.delete(UsageProducer.STREAM_KEY);
        }
    }
}
