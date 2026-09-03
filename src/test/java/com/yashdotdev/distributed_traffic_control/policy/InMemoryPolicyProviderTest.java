package com.yashdotdev.distributed_traffic_control.policy;

import com.yashdotdev.distributed_traffic_control.traffic.TrafficRequest;
import com.yashdotdev.distributed_traffic_control.traffic.TrafficSubject;
import com.yashdotdev.distributed_traffic_control.traffic.TrafficSubjectType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryPolicyProviderTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-09-03T00:00:00Z");

    @Test
    void shouldReturnRegisteredPolicyForMatchingResource() {

        TrafficPolicy defaultPolicy =
                createPolicy(
                        "default-policy",
                        TrafficPolicyType.TOKEN_BUCKET
                );

        TrafficPolicy ordersPolicy =
                createPolicy(
                        "orders-policy",
                        TrafficPolicyType.FIXED_WINDOW
                );

        InMemoryPolicyProvider provider =
                new InMemoryPolicyProvider(defaultPolicy);

        provider.registerPolicy(
                "/api/orders",
                ordersPolicy
        );

        TrafficRequest request =
                createRequest("/api/orders");

        Optional<TrafficPolicy> result =
                provider.findPolicy(request);

        assertTrue(result.isPresent());
        assertSame(
                ordersPolicy,
                result.get()
        );
    }

    @Test
    void shouldReturnDefaultPolicyWhenResourceHasNoRegisteredPolicy() {

        TrafficPolicy defaultPolicy =
                createPolicy(
                        "default-policy",
                        TrafficPolicyType.TOKEN_BUCKET
                );

        InMemoryPolicyProvider provider =
                new InMemoryPolicyProvider(defaultPolicy);

        TrafficRequest request =
                createRequest("/api/products");

        Optional<TrafficPolicy> result =
                provider.findPolicy(request);

        assertTrue(result.isPresent());
        assertSame(
                defaultPolicy,
                result.get()
        );
    }

    @Test
    void shouldReturnEmptyWhenNoPolicyIsRegistered() {

        InMemoryPolicyProvider provider =
                new InMemoryPolicyProvider();

        TrafficRequest request =
                createRequest("/api/products");

        Optional<TrafficPolicy> result =
                provider.findPolicy(request);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReplaceExistingPolicyForSameResource() {

        TrafficPolicy firstPolicy =
                createPolicy(
                        "first-policy",
                        TrafficPolicyType.TOKEN_BUCKET
                );

        TrafficPolicy secondPolicy =
                createPolicy(
                        "second-policy",
                        TrafficPolicyType.FIXED_WINDOW
                );

        InMemoryPolicyProvider provider =
                new InMemoryPolicyProvider();

        provider.registerPolicy(
                "/api/orders",
                firstPolicy
        );

        provider.registerPolicy(
                "/api/orders",
                secondPolicy
        );

        TrafficRequest request =
                createRequest("/api/orders");

        Optional<TrafficPolicy> result =
                provider.findPolicy(request);

        assertTrue(result.isPresent());
        assertSame(
                secondPolicy,
                result.get()
        );
    }

    @Test
    void shouldRemoveRegisteredPolicy() {

        TrafficPolicy policy =
                createPolicy(
                        "orders-policy",
                        TrafficPolicyType.TOKEN_BUCKET
                );

        InMemoryPolicyProvider provider =
                new InMemoryPolicyProvider();

        provider.registerPolicy(
                "/api/orders",
                policy
        );

        provider.removePolicy(
                "/api/orders"
        );

        TrafficRequest request =
                createRequest("/api/orders");

        Optional<TrafficPolicy> result =
                provider.findPolicy(request);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnNewDefaultPolicyAfterDefaultPolicyIsChanged() {

        TrafficPolicy firstDefaultPolicy =
                createPolicy(
                        "default-policy-1",
                        TrafficPolicyType.TOKEN_BUCKET
                );

        TrafficPolicy secondDefaultPolicy =
                createPolicy(
                        "default-policy-2",
                        TrafficPolicyType.SLIDING_WINDOW
                );

        InMemoryPolicyProvider provider =
                new InMemoryPolicyProvider(
                        firstDefaultPolicy
                );

        provider.setDefaultPolicy(
                secondDefaultPolicy
        );

        TrafficRequest request =
                createRequest("/api/products");

        Optional<TrafficPolicy> result =
                provider.findPolicy(request);

        assertTrue(result.isPresent());
        assertSame(
                secondDefaultPolicy,
                result.get()
        );
    }

    @Test
    void shouldRejectBlankResourceDuringRegistration() {

        InMemoryPolicyProvider provider =
                new InMemoryPolicyProvider();

        TrafficPolicy policy =
                createPolicy(
                        "test-policy",
                        TrafficPolicyType.TOKEN_BUCKET
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> provider.registerPolicy(
                        "",
                        policy
                )
        );
    }

    @Test
    void shouldRejectNullPolicyDuringRegistration() {

        InMemoryPolicyProvider provider =
                new InMemoryPolicyProvider();

        assertThrows(
                NullPointerException.class,
                () -> provider.registerPolicy(
                        "/api/orders",
                        null
                )
        );
    }

    @Test
    void shouldRejectNullRequest() {

        TrafficPolicy defaultPolicy =
                createPolicy(
                        "default-policy",
                        TrafficPolicyType.TOKEN_BUCKET
                );

        InMemoryPolicyProvider provider =
                new InMemoryPolicyProvider(
                        defaultPolicy
                );

        assertThrows(
                NullPointerException.class,
                () -> provider.findPolicy((TrafficRequest) null)
        );
    }

    @Test
    void shouldReturnSubjectSpecificPolicyBeforeResourcePolicy() {

        TrafficPolicy resourcePolicy =
                createPolicy(
                        "resource-policy",
                        TrafficPolicyType.TOKEN_BUCKET
                );

        TrafficPolicy subjectPolicy =
                createPolicy(
                        "subject-policy",
                        TrafficPolicyType.FIXED_WINDOW
                );

        InMemoryPolicyProvider provider =
                new InMemoryPolicyProvider(
                        resourcePolicy
                );

        TrafficSubject subject =
                new TrafficSubject(
                        "user-123",
                        TrafficSubjectType.USER
                );

        provider.registerPolicy(
                subject,
                "/api/orders",
                subjectPolicy
        );

        TrafficRequest request =
                new TrafficRequest(
                        "request-1",
                        subject,
                        "/api/orders",
                        CREATED_AT
                );

        Optional<TrafficPolicy> result =
                provider.findPolicy(request);

        assertTrue(result.isPresent());

        assertSame(
                subjectPolicy,
                result.get()
        );
    }

    @Test
    void shouldFallBackToResourcePolicyForDifferentSubject() {

        TrafficPolicy resourcePolicy =
                createPolicy(
                        "resource-policy",
                        TrafficPolicyType.TOKEN_BUCKET
                );

        TrafficPolicy subjectPolicy =
                createPolicy(
                        "subject-policy",
                        TrafficPolicyType.FIXED_WINDOW
                );

        InMemoryPolicyProvider provider =
                new InMemoryPolicyProvider();

        provider.registerPolicy(
                "/api/orders",
                resourcePolicy
        );

        provider.registerPolicy(
                new TrafficSubject(
                        "user-123",
                        TrafficSubjectType.USER
                ),
                "/api/orders",
                subjectPolicy
        );

        TrafficRequest request =
                new TrafficRequest(
                        "request-2",
                        new TrafficSubject(
                                "user-456",
                                TrafficSubjectType.USER
                        ),
                        "/api/orders",
                        CREATED_AT
                );

        Optional<TrafficPolicy> result =
                provider.findPolicy(request);

        assertTrue(result.isPresent());

        assertSame(
                resourcePolicy,
                result.get()
        );
    }


    @Test
    void shouldRemoveSubjectSpecificPolicy() {

        InMemoryPolicyProvider provider =
                new InMemoryPolicyProvider();

        TrafficSubject subject =
                new TrafficSubject(
                        "user-123",
                        TrafficSubjectType.USER
                );

        TrafficPolicy policy =
                createPolicy(
                        "subject-policy",
                        TrafficPolicyType.TOKEN_BUCKET
                );

        provider.registerPolicy(
                subject,
                "/api/orders",
                policy
        );

        provider.removePolicy(
                subject,
                "/api/orders"
        );

        TrafficRequest request =
                new TrafficRequest(
                        "request-1",
                        subject,
                        "/api/orders",
                        CREATED_AT
                );

        assertTrue(
                provider.findPolicy(request).isEmpty()
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
        TrafficSubject subject =
                new TrafficSubject(
                        "user-123",
                        TrafficSubjectType.USER
                );

        return new TrafficRequest(
                "request-" + resource,
                subject,
                resource,
                CREATED_AT
        );
    }
}