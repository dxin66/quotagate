package com.quotagate.auth;

import java.time.LocalDateTime;

public record ApiKey(
        long tenantId,
        String status,
        LocalDateTime expiresAt
) {}