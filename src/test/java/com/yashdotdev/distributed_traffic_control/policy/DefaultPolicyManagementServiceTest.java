package com.yashdotdev.distributed_traffic_control.policy;

import com.yashdotdev.distributed_traffic_control.traffic.TrafficRequest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class DefaultPolicyManagementServiceTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-09-03T00:00:00Z");

    @Test
    void shouldRegisterPolicy() {

        InMemoryPolicyProvider provider =
                new InMemoryPolicyProvider();

        DefaultPolicyManagementService service =
                new DefaultPolicyManagementService(provider);

        TrafficPolicy policy = createPolicy(
                "orders-policy",
                TrafficPolicyType.TOKEN_BUCKET
        );

        TrafficPolicy result =
                service.registerPolicy(
                        "/api/orders",
                        policy
                );

        assertSame(policy, result);

        TrafficRequest request =
                createRequest("/api/orders");

        assertSame(
                policy,
                provider.findPolicy(request).orElseThrow()
        );
    }

    @Test
    void shouldRemovePolicy() {

        InMemoryPolicyProvider provider =
                new InMemoryPolicyProvider();

        DefaultPolicyManagementService service =
                new DefaultPolicyManagementService(provider);

        TrafficPolicy policy = createPolicy(
                "orders-policy",
                TrafficPolicyType.TOKEN_BUCKET
        );

        service.registerPolicy(
                "/api/orders",
                policy
        );

        service.removePolicy("/api/orders");

        TrafficRequest request =
                createRequest("/api/orders");

        assertTrue(
                provider.findPolicy(request).isEmpty()
        );
    }

    @Test
    void shouldSetDefaultPolicy() {

        InMemoryPolicyProvider provider =
                new InMemoryPolicyProvider();

        DefaultPolicyManagementService service =
                new DefaultPolicyManagementService(provider);

        TrafficPolicy policy = createPolicy(
                "default-policy",
                TrafficPolicyType.FIXED_WINDOW
        );

        service.setDefaultPolicy(policy);

        TrafficRequest request =
                createRequest("/api/products");

        assertSame(
                policy,
                provider.findPolicy(request).orElseThrow()
        );
    }

    private TrafficPolicy createPolicy(
            String policyId,
            TrafficPolicyType type
    ) {
        return new TrafficPolicy(
                policyId,
                policyId,
                type,
                PolicyStatus.ACTIVE,
                10,
                2,
                Duration.ofMinutes(1),
                CREATED_AT
        );
    }

    private TrafficRequest createRequest(
            String resource
    ) {
        return new TrafficRequest(
                "request-1",
                new com.yashdotdev.distributed_traffic_control.traffic.TrafficSubject(
                        "user-123",
                        com.yashdotdev.distributed_traffic_control.traffic.TrafficSubjectType.USER
                ),
                resource,
                CREATED_AT
        );
    }
}