package com.quotagate.route;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ModelRouteMapperTest {

    @Autowired
    private ModelRouteMapper modelRouteMapper;

    @Test
    void readsEnabledRoute() {
        List<ModelRoute> routes =
                modelRouteMapper.findEnabledByAlias("gpt-standard");

        assertEquals(1, routes.size());
        ModelRoute route = routes.getFirst();
        assertEquals("mock", route.provider());
        assertEquals("mock", route.upstreamModel());
    }

    @Test
    void unknownAliasHasNoRoute() {
        assertTrue(modelRouteMapper
                .findEnabledByAlias("does-not-exist")
                .isEmpty());
    }
}