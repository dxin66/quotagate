package com.quotagate.usage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "quotagate.usage",
        name = "consumer-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class UsageWorker {

    private final UsageConsumer consumer;

    public UsageWorker(UsageConsumer consumer) {
        this.consumer = consumer;
    }

    @Scheduled(fixedDelayString = "${quotagate.usage.consumer-delay-ms:20}")
    public void consume() {
        consumer.consumeOnce("quotagate-instance", 1_000);
    }
}
