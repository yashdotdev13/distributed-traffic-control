package com.yashdotdev.distributed_traffic_control.traffic;

import com.yashdotdev.distributed_traffic_control.allocation.AllocationProperties;
import com.yashdotdev.distributed_traffic_control.allocation.CapacityAllocator;
import com.yashdotdev.distributed_traffic_control.allocation.FixedAllocationStrategy;
import com.yashdotdev.distributed_traffic_control.allocation.InMemoryCapacityAllocator;
import com.yashdotdev.distributed_traffic_control.lease.*;
import com.yashdotdev.distributed_traffic_control.policy.InMemoryPolicyProvider;
import com.yashdotdev.distributed_traffic_control.policy.PolicyStatus;
import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicy;
import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicyType;
import com.yashdotdev.distributed_traffic_control.quota.InMemoryQuotaCoordinator;
import com.yashdotdev.distributed_traffic_control.quota.QuotaKey;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
                createTrafficDecisionEngine(
                        policy,
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
    void shouldRejectRequestWhenQuotaIsExhausted() {

        TrafficPolicy policy = new TrafficPolicy(
                "default-policy",
                "Default-Traffic-Policy",
                TrafficPolicyType.TOKEN_BUCKET,
                PolicyStatus.ACTIVE,
                1,
                1,
                Instant.now()
        );

        TrafficDecisionEngine trafficDecisionEngine =
                createTrafficDecisionEngine(
                        policy,
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
                createTrafficDecisionEngine(
                        policy,
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
    void shouldNotOverConsumeQuotaWhenRequestsAreConcurrent()
            throws Exception {

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
                createTrafficDecisionEngine(
                        policy,
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

        CountDownLatch startLatch =
                new CountDownLatch(1);

        List<Future<TrafficDecision>> futures =
                new ArrayList<>();

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

        assertEquals(
                capacity,
                allowedRequests
        );
    }

    @Test
    void shouldAllowRequestAfterQuotaIsRefilled() {

        MutableClock clock = new MutableClock(
                Instant.parse("2026-08-28T10:00:00Z")
        );

        TrafficPolicy policy = new TrafficPolicy(
                "refill-policy",
                "Refill Traffic Policy",
                TrafficPolicyType.TOKEN_BUCKET,
                PolicyStatus.ACTIVE,
                1,
                1,
                Instant.now(clock)
        );

        TrafficDecisionEngine trafficDecisionEngine =
                createTrafficDecisionEngine(
                        policy,
                        new InMemoryQuotaCoordinator(clock)
                );

        TrafficRequest request = new TrafficRequest(
                "request-1",
                new TrafficSubject(
                        "user-123",
                        TrafficSubjectType.USER
                ),
                "/api/orders",
                Instant.now(clock)
        );

        TrafficDecision firstDecision =
                trafficDecisionEngine.evaluate(request);

        TrafficDecision secondDecision =
                trafficDecisionEngine.evaluate(request);

        clock.advanceSeconds(1);

        TrafficDecision thirdDecision =
                trafficDecisionEngine.evaluate(request);

        assertTrue(firstDecision.isAllowed());
        assertFalse(secondDecision.isAllowed());
        assertTrue(thirdDecision.isAllowed());
    }

    @Test
    void shouldNotRefillQuotaBeyondMaximumCapacity() {

        MutableClock clock = new MutableClock(
                Instant.parse("2026-08-28T10:00:00Z")
        );

        int capacity = 5;

        TrafficPolicy policy = new TrafficPolicy(
                "capacity-policy",
                "Capacity Traffic Policy",
                TrafficPolicyType.TOKEN_BUCKET,
                PolicyStatus.ACTIVE,
                capacity,
                2,
                Instant.now(clock)
        );

        TrafficDecisionEngine trafficDecisionEngine =
                createTrafficDecisionEngine(
                        policy,
                        new InMemoryQuotaCoordinator(clock)
                );

        TrafficRequest request = new TrafficRequest(
                "request-1",
                new TrafficSubject(
                        "user-123",
                        TrafficSubjectType.USER
                ),
                "/api/orders",
                Instant.now(clock)
        );

        for (int i = 0; i < capacity; i++) {

            TrafficDecision decision =
                    trafficDecisionEngine.evaluate(request);

            assertTrue(decision.isAllowed());
        }

        TrafficDecision exhaustedDecision =
                trafficDecisionEngine.evaluate(request);

        assertFalse(exhaustedDecision.isAllowed());

        clock.advanceSeconds(10);

        int allowedRequests = 0;

        for (int i = 0; i < capacity + 1; i++) {

            TrafficDecision decision =
                    trafficDecisionEngine.evaluate(request);

            if (decision.isAllowed()) {
                allowedRequests++;
            }
        }

        assertEquals(
                capacity,
                allowedRequests
        );
    }


    @Test
    void shouldAllowRequestWhenLocalQuotaIsExhaustedAndCapacityIsAllocated() {

        TrafficPolicy policy = new TrafficPolicy(
                "allocation-policy",
                "Allocation Traffic Policy",
                TrafficPolicyType.TOKEN_BUCKET,
                PolicyStatus.ACTIVE,
                1,
                1,
                Instant.parse("2026-08-28T10:00:00Z")
        );

        InMemoryQuotaCoordinator quotaCoordinator =
                new InMemoryQuotaCoordinator();

        QuotaKey quotaKey = new QuotaKey(
                policy.getPolicyId(),
                new TrafficSubject(
                        "user-123",
                        TrafficSubjectType.USER
                ),
                "/api/orders"
        );

        InMemoryLeaseCoordinator leaseCoordinator =
                new InMemoryLeaseCoordinator();

        leaseCoordinator.registerQuota(
                quotaKey,
                10
        );

        AllocationProperties allocationProperties =
                new AllocationProperties();

        allocationProperties.setNodeId("test-node");
        allocationProperties.setLeaseDuration(
                Duration.ofSeconds(30)
        );

        Clock clock = Clock.fixed(
                Instant.parse("2026-08-28T10:00:00Z"),
                ZoneOffset.UTC
        );

        LeaseStore leaseStore =
                new InMemoryLeaseStore();

        InMemoryCapacityAllocator capacityAllocator =
                new InMemoryCapacityAllocator(
                        leaseCoordinator,
                        new FixedAllocationStrategy(),
                        allocationProperties,
                        leaseStore,
                        clock
                );

        TrafficDecisionEngine trafficDecisionEngine =
                new TrafficDecisionEngine(
                        new InMemoryPolicyProvider(policy),
                        quotaCoordinator,
                        capacityAllocator
                );

        TrafficRequest request = new TrafficRequest(
                "request-1",
                new TrafficSubject(
                        "user-123",
                        TrafficSubjectType.USER
                ),
                "/api/orders",
                Instant.parse("2026-08-28T10:00:00Z")
        );

        TrafficDecision firstDecision =
                trafficDecisionEngine.evaluate(request);

        TrafficDecision secondDecision =
                trafficDecisionEngine.evaluate(request);

        assertTrue(firstDecision.isAllowed());

        assertTrue(secondDecision.isAllowed());
    }

    /**
     * Creates the decision engine for the existing local-quota tests.
     *
     * The allocator intentionally returns no lease because these tests
     * verify the existing local quota behavior. The distributed
     * capacity-allocation behavior will be tested separately.
     */
    private TrafficDecisionEngine createTrafficDecisionEngine(
            TrafficPolicy policy,
            InMemoryQuotaCoordinator quotaCoordinator
    ) {

        CapacityAllocator noOpCapacityAllocator =
                new CapacityAllocator() {

                    @Override
                    public Optional<QuotaLease> allocate(
                            TrafficPolicy trafficPolicy,
                            QuotaKey quotaKey
                    ) {
                        return Optional.empty();
                    }

                    @Override
                    public LeaseConsumptionResult tryConsume(
                            QuotaLease lease,
                            Instant currentTime
                    ) {
                        return new LeaseConsumptionResult(
                                false,
                                lease.getRemainingCapacity()
                        );
                    }
                };

        return new TrafficDecisionEngine(
                new InMemoryPolicyProvider(policy),
                quotaCoordinator,
                noOpCapacityAllocator
        );
    }

    @Test
    void shouldRejectRequestWhenAllocatedLeaseCannotBeConsumed() {

        TrafficPolicy policy = new TrafficPolicy(
                "allocation-policy",
                "Allocation Traffic Policy",
                TrafficPolicyType.TOKEN_BUCKET,
                PolicyStatus.ACTIVE,
                1,
                1,
                Instant.parse("2026-08-28T10:00:00Z")
        );

        InMemoryQuotaCoordinator quotaCoordinator =
                new InMemoryQuotaCoordinator();

        QuotaLease lease = new QuotaLease(
                "lease-1",
                new QuotaKey(
                        policy.getPolicyId(),
                        new TrafficSubject(
                                "user-123",
                                TrafficSubjectType.USER
                        ),
                        "/api/orders"
                ),
                "test-node",
                10,
                Instant.parse("2026-08-28T10:00:00Z"),
                Instant.parse("2026-08-28T10:01:00Z")
        );

        CapacityAllocator capacityAllocator =
                new CapacityAllocator() {

                    @Override
                    public Optional<QuotaLease> allocate(
                            TrafficPolicy trafficPolicy,
                            QuotaKey quotaKey
                    ) {
                        return Optional.of(lease);
                    }

                    @Override
                    public LeaseConsumptionResult tryConsume(
                            QuotaLease quotaLease,
                            Instant currentTime
                    ) {
                        return new LeaseConsumptionResult(
                                false,
                                quotaLease.getRemainingCapacity()
                        );
                    }
                };

        TrafficDecisionEngine trafficDecisionEngine =
                new TrafficDecisionEngine(
                        new InMemoryPolicyProvider(policy),
                        quotaCoordinator,
                        capacityAllocator
                );

        TrafficRequest request = new TrafficRequest(
                "request-1",
                new TrafficSubject(
                        "user-123",
                        TrafficSubjectType.USER
                ),
                "/api/orders",
                Instant.parse("2026-08-28T10:00:00Z")
        );

        // Exhaust local quota.
        TrafficDecision firstDecision =
                trafficDecisionEngine.evaluate(request);

        // Local quota exhausted, allocator returns a lease,
        // but consuming that lease fails.
        TrafficDecision secondDecision =
                trafficDecisionEngine.evaluate(request);

        assertTrue(firstDecision.isAllowed());
        assertFalse(secondDecision.isAllowed());

        assertEquals(
                "Traffic quota exhausted",
                secondDecision.getReason()
        );
    }

    private static class MutableClock extends Clock {

        private Instant currentTime;

        private MutableClock(Instant currentTime) {
            this.currentTime = currentTime;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return currentTime;
        }

        public void advanceSeconds(long seconds) {
            currentTime =
                    currentTime.plusSeconds(seconds);
        }
    }
}