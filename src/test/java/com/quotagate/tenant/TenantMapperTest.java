package com.quotagate.tenant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class TenantMapperTest {

    @Autowired
    private TenantMapper tenantMapper;

    @Test
    void readsLocalTenant() {
        Tenant tenant = tenantMapper.findById(1L);

        assertNotNull(tenant);
        assertEquals("Local Demo", tenant.name());
        assertEquals("ACTIVE", tenant.status());
    }
}