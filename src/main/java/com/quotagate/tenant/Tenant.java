package com.quotagate.tenant;

import java.time.LocalDateTime;

public record Tenant(
        long id,
        String name,
        String status,
        LocalDateTime createdAt
) {}