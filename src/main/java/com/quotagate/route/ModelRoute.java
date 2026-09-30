package com.quotagate.route;

import java.math.BigDecimal;

public record ModelRoute(
        long id,
        String modelAlias,
        String provider,
        String upstreamModel,
        int priority,
        boolean enabled,
        BigDecimal inputPrice,
        BigDecimal outputPrice
) {}
