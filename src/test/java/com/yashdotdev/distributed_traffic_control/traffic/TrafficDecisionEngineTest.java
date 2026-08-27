package com.yashdotdev.distributed_traffic_control.traffic;

import com.yashdotdev.distributed_traffic_control.policy.InMemoryPolicyProvider;
import com.yashdotdev.distributed_traffic_control.policy.PolicyStatus;
import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicy;
import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicyType;
import com.yashdotdev.distributed_traffic_control.quota.InMemoryQuotaCoordinator;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrafficDecisionEngineTest {

    @Test
    void shouldAllowRequestWhenPolicyIsActiveAndCapacityIsAvailable() {

        TrafficPolicy policy = new TrafficPolicy(
                "default-policy",
                "Default Traffic Policy",
                TrafficPolicyType.TOKEN_BUCKET,
                PolicyStatus.ACTIVE,
                100,
                10,
                Instant.now()
        );

        TrafficDecisionEngine trafficDecisionEngine =
                new TrafficDecisionEngine(
                        new InMemoryPolicyProvider(policy),
                        new InMemoryQuotaCoordinator()
                );

        TrafficRequest request = new TrafficRequest(
                "request-1",
                new TrafficSubject(
                        "user-123",
                        TrafficSubjectType.USER
                ),
                "/api/orders",
                Instant.now()
        );

        TrafficDecision decision =
                trafficDecisionEngine.evaluate(request);

        assertTrue(decision.isAllowed());
    }

    @Test
    void shouldRejectRequestWhenQuotaIsExhausted(){

        TrafficPolicy policy = new TrafficPolicy(
                "default-policy",
                "Default-Traffic-Policy",
                TrafficPolicyType.TOKEN_BUCKET,
                PolicyStatus.ACTIVE,
                1,
                1,
                Instant.now()
        );

        TrafficDecisionEngine trafficDecisionEngine = new TrafficDecisionEngine(
                new InMemoryPolicyProvider(policy),
                new InMemoryQuotaCoordinator()
        );

        TrafficRequest request = new TrafficRequest(
                "request-1",
                new TrafficSubject(
                        "user-123",
                        TrafficSubjectType.USER
                ),
                "/api/orders",
                Instant.now()
        );

        TrafficDecision firstDecision =
                trafficDecisionEngine.evaluate(request);

        TrafficDecision secondDecision =
                trafficDecisionEngine.evaluate(request);

        assertTrue(firstDecision.isAllowed());
        assertFalse(secondDecision.isAllowed());
    }

    @Test
    void shouldRejectRequestWhenPolicyIsInactive() {

        TrafficPolicy policy = new TrafficPolicy(
                "inactive-policy",
                "Inactive Traffic Policy",
                TrafficPolicyType.TOKEN_BUCKET,
                PolicyStatus.INACTIVE,
                100,
                10,
                Instant.now()
        );

        TrafficDecisionEngine trafficDecisionEngine =
                new TrafficDecisionEngine(
                        new InMemoryPolicyProvider(policy),
                        new InMemoryQuotaCoordinator()
                );

        TrafficRequest request = new TrafficRequest(
                "request-1",
                new TrafficSubject(
                        "user-123",
                        TrafficSubjectType.USER
                ),
                "/api/orders",
                Instant.now()
        );

        TrafficDecision decision =
                trafficDecisionEngine.evaluate(request);

        assertFalse(decision.isAllowed());
    }
}