package com.yashdotdev.distributed_traffic_control.policy;

import com.yashdotdev.distributed_traffic_control.traffic.TrafficSubject;
import com.yashdotdev.distributed_traffic_control.traffic.TrafficSubjectType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
class RedisPolicyStoreIntegrationTest {

    private static final String DEFAULT_POLICY_KEY =
            "traffic-control:policies:default";

    private static final String RESOURCE_INDEX_KEY =
            "traffic-control:policies:resources";

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine")
                    .withExposedPorts(6379);

    @Autowired
    private StringRedisTemplate redisTemplate;

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add(
                "spring.data.redis.port",
                () -> REDIS.getMappedPort(6379)
        );
    }

    @BeforeEach
    void cleanRedis() {
        redisTemplate.delete(DEFAULT_POLICY_KEY);
        redisTemplate.delete(RESOURCE_INDEX_KEY);

        var keys = redisTemplate.keys("traffic-control:policies:resource:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        keys = redisTemplate.keys("traffic-control:policies:subject:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void shouldPersistResourcePolicyAcrossStoreInstances() {
        RedisPolicyStore firstStore = new RedisPolicyStore(redisTemplate);
        RedisPolicyStore secondStore = new RedisPolicyStore(redisTemplate);

        String resource = "/api/orders/" + UUID.randomUUID();

        TrafficPolicy policy = new TrafficPolicy(
                "policy-" + UUID.randomUUID(),
                "Orders Policy",
                TrafficPolicyType.TOKEN_BUCKET,
                PolicyStatus.ACTIVE,
                200,
                20,
                Instant.parse("2026-01-01T10:00:00Z")
        );

        firstStore.registerPolicy(resource, policy);

        TrafficPolicy storedPolicy = secondStore.findPolicy(resource)
                .orElseThrow(() -> new AssertionError(
                        "Expected the resource policy to be persisted"
                ));

        assertEquals(policy.getPolicyId(), storedPolicy.getPolicyId());
        assertEquals(policy.getName(), storedPolicy.getName());
        assertEquals(policy.getType(), storedPolicy.getType());
        assertEquals(policy.getStatus(), storedPolicy.getStatus());
        assertEquals(policy.getCapacity(), storedPolicy.getCapacity());
        assertEquals(policy.getRefillRate(), storedPolicy.getRefillRate());
        assertEquals(policy.getWindowDuration(), storedPolicy.getWindowDuration());
        assertEquals(policy.getCreatedAt(), storedPolicy.getCreatedAt());
    }

    @Test
    void shouldPersistDefaultPolicyAcrossStoreInstances() {
        RedisPolicyStore firstStore = new RedisPolicyStore(redisTemplate);
        RedisPolicyStore secondStore = new RedisPolicyStore(redisTemplate);

        TrafficPolicy policy = new TrafficPolicy(
                "default-" + UUID.randomUUID(),
                "Default Policy",
                TrafficPolicyType.TOKEN_BUCKET,
                PolicyStatus.ACTIVE,
                100,
                10,
                Instant.parse("2026-01-01T12:00:00Z")
        );

        firstStore.setDefaultPolicy(policy);

        TrafficPolicy storedPolicy = secondStore.findDefaultPolicy()
                .orElseThrow(() -> new AssertionError(
                        "Expected the default policy to be persisted"
                ));

        assertEquals(policy.getPolicyId(), storedPolicy.getPolicyId());
        assertEquals(policy.getName(), storedPolicy.getName());
        assertEquals(policy.getType(), storedPolicy.getType());
        assertEquals(policy.getStatus(), storedPolicy.getStatus());
        assertEquals(policy.getCapacity(), storedPolicy.getCapacity());
        assertEquals(policy.getRefillRate(), storedPolicy.getRefillRate());
        assertEquals(policy.getWindowDuration(), storedPolicy.getWindowDuration());
        assertEquals(policy.getCreatedAt(), storedPolicy.getCreatedAt());
    }

    @Test
    void shouldPreserveExistingDefaultPolicyDuringInitialization() {
        RedisPolicyStore store = new RedisPolicyStore(redisTemplate);

        TrafficPolicy existingPolicy = new TrafficPolicy(
                "existing-" + UUID.randomUUID(),
                "Existing Default Policy",
                TrafficPolicyType.TOKEN_BUCKET,
                PolicyStatus.ACTIVE,
                150,
                15,
                Instant.parse("2026-01-01T08:00:00Z")
        );

        TrafficPolicy initialPolicy = new TrafficPolicy(
                "initial-" + UUID.randomUUID(),
                "Initial Default Policy",
                TrafficPolicyType.TOKEN_BUCKET,
                PolicyStatus.ACTIVE,
                100,
                10,
                Instant.parse("2026-01-01T09:00:00Z")
        );

        store.setDefaultPolicy(existingPolicy);
        store.initializeDefaultPolicy(initialPolicy);

        TrafficPolicy storedPolicy = store.findDefaultPolicy()
                .orElseThrow(() -> new AssertionError(
                        "Expected the default policy to exist"
                ));

        assertEquals(
                existingPolicy.getPolicyId(),
                storedPolicy.getPolicyId()
        );
        assertEquals(
                existingPolicy.getName(),
                storedPolicy.getName()
        );
        assertEquals(
                existingPolicy.getCapacity(),
                storedPolicy.getCapacity()
        );
    }

    @Test
    void shouldPersistSubjectSpecificPolicyAcrossStoreInstances() {
        RedisPolicyStore firstStore = new RedisPolicyStore(redisTemplate);
        RedisPolicyStore secondStore = new RedisPolicyStore(redisTemplate);

        TrafficSubject originalSubject = new TrafficSubject(
                "user-" + UUID.randomUUID(),
                TrafficSubjectType.USER
        );

        TrafficSubject equivalentSubject = new TrafficSubject(
                originalSubject.getSubjectId(),
                originalSubject.getType()
        );

        String resource = "/api/payments";

        TrafficPolicy policy = new TrafficPolicy(
                "subject-" + UUID.randomUUID(),
                "User Payment Policy",
                TrafficPolicyType.TOKEN_BUCKET,
                PolicyStatus.ACTIVE,
                50,
                5,
                Instant.parse("2026-01-01T11:00:00Z")
        );

        firstStore.registerPolicy(originalSubject, resource, policy);

        TrafficPolicy storedPolicy = secondStore
                .findPolicy(equivalentSubject, resource)
                .orElseThrow(() -> new AssertionError(
                        "Expected the subject-specific policy to be persisted"
                ));

        assertEquals(policy.getPolicyId(), storedPolicy.getPolicyId());
        assertEquals(policy.getName(), storedPolicy.getName());
        assertEquals(policy.getCapacity(), storedPolicy.getCapacity());
        assertEquals(policy.getRefillRate(), storedPolicy.getRefillRate());
    }

    @Test
    void shouldListRegisteredResourcePolicies() {
        RedisPolicyStore store = new RedisPolicyStore(redisTemplate);

        String resource = "/api/products/" + UUID.randomUUID();

        TrafficPolicy policy = new TrafficPolicy(
                "list-" + UUID.randomUUID(),
                "Products Policy",
                TrafficPolicyType.TOKEN_BUCKET,
                PolicyStatus.ACTIVE,
                300,
                30,
                Instant.parse("2026-01-01T13:00:00Z")
        );

        store.registerPolicy(resource, policy);

        PolicyRegistration registration = store.findAllPolicies()
                .stream()
                .filter(item -> item.resource().equals(resource))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Expected the registered resource policy to be listed"
                ));

        assertEquals(resource, registration.resource());
        assertEquals(policy.getPolicyId(), registration.policy().getPolicyId());
    }
}