package com.quotagate.auth;

import com.quotagate.tenant.Tenant;
import com.quotagate.tenant.TenantCache;
import com.quotagate.tenant.TenantMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;



@Service
public class ApiKeyAuthService {
    private final ApiKeyCache apiKeyCache;
    private final TenantCache tenantCache;

    public ApiKeyAuthService(ApiKeyCache apiKeyCache, TenantCache tenantCache) {
        this.apiKeyCache = apiKeyCache;
        this.tenantCache = tenantCache;
    }

    public Long authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }

        String rawKey = authorization.substring("Bearer ".length());
        if (!rawKey.startsWith("qg_sk_")) {
            return null;
        }

        ApiKey apiKey = apiKeyCache.findByHash(sha256(rawKey));
        if (apiKey == null || !"ACTIVE".equals(apiKey.status())) {
            return null;
        }
        if (apiKey.expiresAt() != null
                && !apiKey.expiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
            return null;
        }

        Tenant tenant = tenantCache.findById(apiKey.tenantId());
        if (tenant == null || !"ACTIVE".equals(tenant.status())) {
            return null;
        }

        return tenant.id();
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}