package com.yashdotdev.distributed_traffic_control.config;

import com.yashdotdev.distributed_traffic_control.lease.LeaseCoordinator;
import com.yashdotdev.distributed_traffic_control.lease.RedisLeaseCoordinator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

class RedisKeyConfigurationIntegrationTest {

    @SpringBootTest
    @Nested
    class DefaultConfigurationTest {
        @Autowired
        private RedisKeyProperties properties;

        @Autowired
        private RedisKeyBuilder keyBuilder;

        @Autowired
        private LeaseCoordinator leaseCoordinator;

        @Test
        void shouldLoadDefaultKeyConfiguration() {
            assertNotNull(properties);
            assertEquals("traffic-control", properties.getKeyNamespace());

            assertNotNull(keyBuilder);
            assertEquals("traffic-control", keyBuilder.getNamespace());

            assertNotNull(leaseCoordinator);
            assertInstanceOf(RedisLeaseCoordinator.class, leaseCoordinator);
            RedisLeaseCoordinator redisLeaseCoordinator = (RedisLeaseCoordinator) leaseCoordinator;
            assertEquals("traffic-control", redisLeaseCoordinator.getKeyBuilder().getNamespace());
        }
    }

    @SpringBootTest
    @TestPropertySource(properties = "traffic-control.redis.key-namespace=staging:traffic-control")
    @Nested
    class CustomConfigurationTest {
        @Autowired
        private RedisKeyProperties properties;

        @Autowired
        private RedisKeyBuilder keyBuilder;

        @Autowired
        private LeaseCoordinator leaseCoordinator;

        @Test
        void shouldLoadCustomKeyConfiguration() {
            assertNotNull(properties);
            assertEquals("staging:traffic-control", properties.getKeyNamespace());

            assertNotNull(keyBuilder);
            assertEquals("staging:traffic-control", keyBuilder.getNamespace());

            assertNotNull(leaseCoordinator);
            assertInstanceOf(RedisLeaseCoordinator.class, leaseCoordinator);
            RedisLeaseCoordinator redisLeaseCoordinator = (RedisLeaseCoordinator) leaseCoordinator;
            assertEquals("staging:traffic-control", redisLeaseCoordinator.getKeyBuilder().getNamespace());
        }
    }
}
