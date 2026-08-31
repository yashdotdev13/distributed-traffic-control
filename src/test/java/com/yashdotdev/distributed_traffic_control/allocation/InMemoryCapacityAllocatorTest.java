package com.yashdotdev.distributed_traffic_control.allocation;

import com.yashdotdev.distributed_traffic_control.lease.InMemoryLeaseCoordinator;
import com.yashdotdev.distributed_traffic_control.lease.InMemoryLeaseStore;
import com.yashdotdev.distributed_traffic_control.lease.LeaseConsumptionResult;
import com.yashdotdev.distributed_traffic_control.lease.QuotaLease;
import com.yashdotdev.distributed_traffic_control.policy.PolicyStatus;
import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicy;
import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicyType;
import com.yashdotdev.distributed_traffic_control.quota.QuotaKey;
import com.yashdotdev.distributed_traffic_control.traffic.TrafficSubject;
import com.yashdotdev.distributed_traffic_control.traffic.TrafficSubjectType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryCapacityAllocatorTest {

    @Test
    void shouldAllocateCapacityUsingConfiguredNodeAndLeaseDuration() {

        Clock clock = Clock.fixed(
                Instant.parse("2026-08-28T10:00:00Z"),
                ZoneOffset.UTC
        );

        InMemoryLeaseCoordinator leaseCoordinator =
                new InMemoryLeaseCoordinator(clock);

        AllocationStrategy allocationStrategy =
                new FixedAllocationStrategy();

        AllocationProperties allocationProperties =
                new AllocationProperties();

        allocationProperties.setNodeId("node-1");
        allocationProperties.setLeaseDuration(
                Duration.ofSeconds(60)
        );

        InMemoryLeaseStore leaseStore =
                new InMemoryLeaseStore();

        InMemoryCapacityAllocator capacityAllocator =
                new InMemoryCapacityAllocator(
                        leaseCoordinator,
                        allocationStrategy,
                        allocationProperties,
                        leaseStore,
                        clock
                );

        QuotaKey quotaKey = createQuotaKey();

        TrafficPolicy policy = new TrafficPolicy(
                "test-policy",
                "Test Policy",
                TrafficPolicyType.TOKEN_BUCKET,
                PolicyStatus.ACTIVE,
                40,
                10,
                Instant.parse("2026-08-28T09:00:00Z")
        );

        leaseCoordinator.registerQuota(
                quotaKey,
                100
        );

        Optional<QuotaLease> lease =
                capacityAllocator.allocate(
                        policy,
                        quotaKey
                );

        assertTrue(lease.isPresent());

        assertEquals(
                "node-1",
                lease.get().getNodeId()
        );

        assertEquals(
                40,
                lease.get().getAllocatedCapacity()
        );

        assertEquals(
                40,
                lease.get().getRemainingCapacity()
        );

        assertEquals(
                Instant.parse("2026-08-28T10:01:00Z"),
                lease.get().getExpiresAt()
        );
    }

    @Test
    void shouldConsumeCapacityFromAllocatedLease() {

        Clock clock = Clock.fixed(
                Instant.parse("2026-08-28T10:00:00Z"),
                ZoneOffset.UTC
        );

        InMemoryLeaseCoordinator leaseCoordinator =
                new InMemoryLeaseCoordinator(clock);

        AllocationStrategy allocationStrategy =
                new FixedAllocationStrategy();

        AllocationProperties allocationProperties =
                new AllocationProperties();

        allocationProperties.setNodeId("node-1");
        allocationProperties.setLeaseDuration(
                Duration.ofSeconds(60)
        );

        InMemoryLeaseStore leaseStore =
                new InMemoryLeaseStore();

        InMemoryCapacityAllocator capacityAllocator =
                new InMemoryCapacityAllocator(
                        leaseCoordinator,
                        allocationStrategy,
                        allocationProperties,
                        leaseStore,
                        clock
                );

        QuotaKey quotaKey = createQuotaKey();

        TrafficPolicy policy = new TrafficPolicy(
                "test-policy",
                "Test Policy",
                TrafficPolicyType.TOKEN_BUCKET,
                PolicyStatus.ACTIVE,
                40,
                10,
                Instant.parse("2026-08-28T09:00:00Z")
        );

        leaseCoordinator.registerQuota(
                quotaKey,
                100
        );

        Optional<QuotaLease> lease =
                capacityAllocator.allocate(
                        policy,
                        quotaKey
                );

        assertTrue(lease.isPresent());

        QuotaLease quotaLease = lease.get();

        assertEquals(
                40,
                quotaLease.getRemainingCapacity()
        );

        LeaseConsumptionResult result =
                leaseCoordinator.tryConsume(
                        quotaLease,
                        "node-1",
                        clock.instant()
                );

        assertTrue(result.isConsumed());

        assertEquals(
                39,
                result.getRemainingCapacity()
        );

        assertEquals(
                39,
                quotaLease.getRemainingCapacity()
        );
    }

    @Test
    void shouldConsumeCapacityThroughLeaseCoordinator() {

        Clock clock = Clock.fixed(
                Instant.parse("2026-08-28T10:00:00Z"),
                ZoneOffset.UTC
        );

        InMemoryLeaseCoordinator leaseCoordinator =
                new InMemoryLeaseCoordinator(clock);

        AllocationStrategy allocationStrategy =
                new FixedAllocationStrategy();

        AllocationProperties allocationProperties =
                new AllocationProperties();

        allocationProperties.setNodeId("node-1");
        allocationProperties.setLeaseDuration(
                Duration.ofSeconds(60)
        );

        InMemoryLeaseStore leaseStore =
                new InMemoryLeaseStore();

        InMemoryCapacityAllocator capacityAllocator =
                new InMemoryCapacityAllocator(
                        leaseCoordinator,
                        allocationStrategy,
                        allocationProperties,
                        leaseStore,
                        clock
                );

        QuotaKey quotaKey = createQuotaKey();

        TrafficPolicy policy = new TrafficPolicy(
                "test-policy",
                "Test Policy",
                TrafficPolicyType.TOKEN_BUCKET,
                PolicyStatus.ACTIVE,
                40,
                10,
                Instant.parse("2026-08-28T09:00:00Z")
        );

        leaseCoordinator.registerQuota(
                quotaKey,
                100
        );

        Optional<QuotaLease> lease =
                capacityAllocator.allocate(
                        policy,
                        quotaKey
                );

        assertTrue(lease.isPresent());

        assertEquals(
                40,
                lease.get().getRemainingCapacity()
        );

        LeaseConsumptionResult result =
                capacityAllocator.tryConsume(
                        lease.get(),
                        clock.instant()
                );

        assertTrue(result.isConsumed());

        assertEquals(
                39,
                result.getRemainingCapacity()
        );

        assertEquals(
                39,
                lease.get().getRemainingCapacity()
        );
    }

    @Test
    void shouldReuseExistingValidLease() {

        Clock clock = Clock.fixed(
                Instant.parse("2026-08-28T10:00:00Z"),
                ZoneOffset.UTC
        );

        InMemoryLeaseCoordinator leaseCoordinator =
                new InMemoryLeaseCoordinator(clock);

        AllocationProperties allocationProperties =
                new AllocationProperties();

        allocationProperties.setNodeId("node-1");
        allocationProperties.setLeaseDuration(
                Duration.ofSeconds(60)
        );

        InMemoryLeaseStore leaseStore =
                new InMemoryLeaseStore();

        InMemoryCapacityAllocator capacityAllocator =
                new InMemoryCapacityAllocator(
                        leaseCoordinator,
                        new FixedAllocationStrategy(),
                        allocationProperties,
                        leaseStore,
                        clock
                );

        QuotaKey quotaKey = createQuotaKey();

        TrafficPolicy policy = new TrafficPolicy(
                "test-policy",
                "Test Policy",
                TrafficPolicyType.TOKEN_BUCKET,
                PolicyStatus.ACTIVE,
                40,
                10,
                Instant.parse("2026-08-28T09:00:00Z")
        );

        leaseCoordinator.registerQuota(
                quotaKey,
                100
        );

        Optional<QuotaLease> firstLease =
                capacityAllocator.allocate(
                        policy,
                        quotaKey
                );

        assertTrue(firstLease.isPresent());

        Optional<QuotaLease> secondLease =
                capacityAllocator.allocate(
                        policy,
                        quotaKey
                );

        assertTrue(secondLease.isPresent());

        assertSame(
                firstLease.get(),
                secondLease.get()
        );

        assertEquals(
                40,
                secondLease.get().getAllocatedCapacity()
        );

        assertEquals(
                40,
                secondLease.get().getRemainingCapacity()
        );
    }

    @Test
    void shouldAcquireNewLeaseWhenExistingLeaseIsExhausted() {

        Clock clock = Clock.fixed(
                Instant.parse("2026-08-28T10:00:00Z"),
                ZoneOffset.UTC
        );

        InMemoryLeaseCoordinator leaseCoordinator =
                new InMemoryLeaseCoordinator(clock);

        AllocationProperties allocationProperties =
                new AllocationProperties();

        allocationProperties.setNodeId("node-1");
        allocationProperties.setLeaseDuration(
                Duration.ofSeconds(60)
        );

        InMemoryLeaseStore leaseStore =
                new InMemoryLeaseStore();

        InMemoryCapacityAllocator capacityAllocator =
                new InMemoryCapacityAllocator(
                        leaseCoordinator,
                        new FixedAllocationStrategy(),
                        allocationProperties,
                        leaseStore,
                        clock
                );

        QuotaKey quotaKey = createQuotaKey();

        TrafficPolicy policy = new TrafficPolicy(
                "test-policy",
                "Test Policy",
                TrafficPolicyType.TOKEN_BUCKET,
                PolicyStatus.ACTIVE,
                2,
                10,
                Instant.parse("2026-08-28T09:00:00Z")
        );

        /*
         * Four units of global capacity allow two
         * separate leases of two units each.
         */
        leaseCoordinator.registerQuota(
                quotaKey,
                4
        );

        Optional<QuotaLease> firstLease =
                capacityAllocator.allocate(
                        policy,
                        quotaKey
                );

        assertTrue(firstLease.isPresent());

        QuotaLease lease = firstLease.get();

        assertEquals(
                2,
                lease.getRemainingCapacity()
        );

        LeaseConsumptionResult firstConsumption =
                capacityAllocator.tryConsume(
                        lease,
                        clock.instant()
                );

        assertTrue(firstConsumption.isConsumed());

        assertEquals(
                1,
                firstConsumption.getRemainingCapacity()
        );

        LeaseConsumptionResult secondConsumption =
                capacityAllocator.tryConsume(
                        lease,
                        clock.instant()
                );

        assertTrue(secondConsumption.isConsumed());

        assertEquals(
                0,
                secondConsumption.getRemainingCapacity()
        );

        assertTrue(
                leaseStore.find(quotaKey).isEmpty()
        );

        Optional<QuotaLease> secondLease =
                capacityAllocator.allocate(
                        policy,
                        quotaKey
                );

        assertTrue(secondLease.isPresent());

        assertNotSame(
                firstLease.get(),
                secondLease.get()
        );

        assertEquals(
                2,
                secondLease.get().getAllocatedCapacity()
        );

        assertEquals(
                2,
                secondLease.get().getRemainingCapacity()
        );
    }

    @Test
    void shouldAcquireNewLeaseWhenExistingLeaseIsExpired() {

        Clock clock = Clock.fixed(
                Instant.parse("2026-08-28T10:02:00Z"),
                ZoneOffset.UTC
        );

        InMemoryLeaseCoordinator leaseCoordinator =
                new InMemoryLeaseCoordinator(clock);

        AllocationProperties allocationProperties =
                new AllocationProperties();

        allocationProperties.setNodeId("node-1");
        allocationProperties.setLeaseDuration(
                Duration.ofSeconds(60)
        );

        InMemoryLeaseStore leaseStore =
                new InMemoryLeaseStore();

        InMemoryCapacityAllocator capacityAllocator =
                new InMemoryCapacityAllocator(
                        leaseCoordinator,
                        new FixedAllocationStrategy(),
                        allocationProperties,
                        leaseStore,
                        clock
                );

        QuotaKey quotaKey = createQuotaKey();

        TrafficPolicy policy = new TrafficPolicy(
                "test-policy",
                "Test Policy",
                TrafficPolicyType.TOKEN_BUCKET,
                PolicyStatus.ACTIVE,
                2,
                10,
                Instant.parse("2026-08-28T09:00:00Z")
        );

        leaseCoordinator.registerQuota(
                quotaKey,
                2
        );

        /*
         * Manually create a lease that was issued at 10:00
         * and expired at 10:01.
         */
        QuotaLease expiredLease = new QuotaLease(
                "expired-lease",
                quotaKey,
                "node-1",
                2,
                Instant.parse("2026-08-28T10:00:00Z"),
                Instant.parse("2026-08-28T10:01:00Z")
        );

        leaseStore.save(expiredLease);

        /*
         * The expired lease is only in the local store.
         * The coordinator does not own this manually-created lease,
         * so allocate() must remove the stale local lease and
         * then acquire a fresh lease from the coordinator.
         */
        Optional<QuotaLease> newLease =
                capacityAllocator.allocate(
                        policy,
                        quotaKey
                );

        assertTrue(newLease.isPresent());

        assertNotSame(
                expiredLease,
                newLease.get()
        );

        assertEquals(
                2,
                newLease.get().getAllocatedCapacity()
        );

        assertEquals(
                2,
                newLease.get().getRemainingCapacity()
        );

        assertEquals(
                "node-1",
                newLease.get().getNodeId()
        );

        assertEquals(
                Instant.parse("2026-08-28T10:02:00Z"),
                newLease.get().getIssuedAt()
        );

        assertTrue(
                leaseStore.find(quotaKey).isPresent()
        );

        assertSame(
                newLease.get(),
                leaseStore.find(quotaKey).get()
        );
    }

    @Test
    void shouldRejectConsumptionFromDifferentNode() {

        Clock clock = Clock.fixed(
                Instant.parse("2026-08-28T10:00:00Z"),
                ZoneOffset.UTC
        );

        InMemoryLeaseCoordinator leaseCoordinator =
                new InMemoryLeaseCoordinator(clock);

        QuotaKey quotaKey = createQuotaKey();

        leaseCoordinator.registerQuota(
                quotaKey,
                100
        );

        Optional<QuotaLease> lease =
                leaseCoordinator.acquireLease(
                        quotaKey,
                        "node-a",
                        20,
                        Duration.ofSeconds(60)
                );

        assertTrue(lease.isPresent());

        QuotaLease quotaLease = lease.get();

        assertEquals(
                "node-a",
                quotaLease.getNodeId()
        );

        /*
         * The owner of the lease should be able to consume it.
         */
        LeaseConsumptionResult ownerResult =
                leaseCoordinator.tryConsume(
                        quotaLease,
                        "node-a",
                        clock.instant()
                );

        assertTrue(ownerResult.isConsumed());

        assertEquals(
                19,
                ownerResult.getRemainingCapacity()
        );

        /*
         * A different node must not be allowed
         * to consume the same lease.
         */
        LeaseConsumptionResult otherNodeResult =
                leaseCoordinator.tryConsume(
                        quotaLease,
                        "node-b",
                        clock.instant()
                );

        assertFalse(otherNodeResult.isConsumed());

        assertEquals(
                19,
                otherNodeResult.getRemainingCapacity()
        );

        /*
         * The unauthorized attempt must not change
         * the remaining capacity.
         */
        assertEquals(
                19,
                quotaLease.getRemainingCapacity()
        );
    }

    private QuotaKey createQuotaKey() {

        return new QuotaKey(
                "test-policy",
                new TrafficSubject(
                        "user-123",
                        TrafficSubjectType.USER
                ),
                "/api/orders"
        );
    }
}