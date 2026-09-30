package com.quotagate.route;

import java.util.List;

public record CachedModelRoutes(
        List<ModelRoute> routes,
        long expiresAtEpochMilli
) {
    public CachedModelRoutes {
        routes = List.copyOf(routes);
    }

    public boolean isExpired(long nowEpochMilli) {
        return nowEpochMilli >= expiresAtEpochMilli;
    }
}