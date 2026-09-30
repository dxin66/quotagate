package com.quotagate.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class ApiKeyMapperTest {

    @Autowired
    private ApiKeyMapper apiKeyMapper;

    @Test
    void readsApiKeyByHash() {
        String hash = "d9ad46b64ef3a0e2151449ce7db2bb4bf0c7aa815178c519bb251dbe2f088230";

        ApiKey apiKey = apiKeyMapper.findByHash(hash);

        assertNotNull(apiKey);
        assertEquals(1L, apiKey.tenantId());
        assertEquals("ACTIVE", apiKey.status());
    }
}