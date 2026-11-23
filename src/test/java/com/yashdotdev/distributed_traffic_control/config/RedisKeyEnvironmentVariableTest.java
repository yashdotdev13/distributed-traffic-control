package com.yashdotdev.distributed_traffic_control.config;

import com.yashdotdev.distributed_traffic_control.lease.LeaseCoordinator;
import com.yashdotdev.distributed_traffic_control.lease.RedisLeaseCoordinator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RedisKeyEnvironmentVariableTest {

    @Autowired
    private RedisKeyProperties properties;

    @Autowired
    private RedisKeyBuilder keyBuilder;

    @Autowired
    private LeaseCoordinator leaseCoordinator;

    @Test
    void shouldPickUpConfiguredNamespaceFromEnvironmentOrProperty() {
        String expected = System.getenv("REDIS_KEY_NAMESPACE");
        if (expected == null || expected.isBlank()) {
            expected = "traffic-control";
        }
        assertEquals(expected, properties.getKeyNamespace());
        assertEquals(expected, keyBuilder.getNamespace());
        assertInstanceOf(RedisLeaseCoordinator.class, leaseCoordinator);
        assertEquals(expected, ((RedisLeaseCoordinator) leaseCoordinator).getKeyBuilder().getNamespace());
    }
}
