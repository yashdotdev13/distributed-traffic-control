package com.yashdotdev.distributed_traffic_control.traffic;

import com.yashdotdev.distributed_traffic_control.policy.InMemoryPolicyProvider;
import com.yashdotdev.distributed_traffic_control.policy.PolicyStatus;
import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicy;
import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicyType;
import com.yashdotdev.distributed_traffic_control.quota.InMemoryQuotaCoordinator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    void shouldNotOverConsumeQuotaWhenRequestsAreConcurrent() throws Exception {

        int capacity = 10;
        int numberOfRequests = 100;

        TrafficPolicy policy = new TrafficPolicy(
                "concurrent-policy",
                "Concurrent Traffic Policy",
                TrafficPolicyType.TOKEN_BUCKET,
                PolicyStatus.ACTIVE,
                capacity,
                1,
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

        ExecutorService executorService =
                Executors.newFixedThreadPool(numberOfRequests);

        CountDownLatch startLatch = new CountDownLatch(1);

        List<Future<TrafficDecision>> futures = new ArrayList<>();

        for (int i = 0; i < numberOfRequests; i++) {
            futures.add(
                    executorService.submit(() -> {
                        startLatch.await();

                        return trafficDecisionEngine.evaluate(request);
                    })
            );
        }

        startLatch.countDown();

        int allowedRequests = 0;

        for (Future<TrafficDecision> future : futures) {
            TrafficDecision decision = future.get();

            if (decision.isAllowed()) {
                allowedRequests++;
            }
        }

        executorService.shutdown();
        assertEquals(capacity, allowedRequests);
    }
}