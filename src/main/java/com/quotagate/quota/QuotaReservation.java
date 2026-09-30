package com.quotagate.quota;

public record QuotaReservation(
        long tenantId,
        String month,
        long estimatedTokens
) {
}