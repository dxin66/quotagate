package com.quotagate.usage;

public record UsageEvent(
        String requestId,
        long tenantId,
        String provider,
        long inputTokens,
        long outputTokens,
        String eventType
) {
    public UsageEvent {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId cannot be blank");
        }
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("provider cannot be blank");
        }
        if (inputTokens < 0 || outputTokens < 0) {
            throw new IllegalArgumentException("token counts cannot be negative");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType cannot be blank");
        }
    }
}