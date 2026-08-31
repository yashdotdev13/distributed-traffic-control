package com.yashdotdev.distributed_traffic_control.allocation;

import com.yashdotdev.distributed_traffic_control.lease.InMemoryLeaseCoordinator;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        InMemoryCapacityAllocator capacityAllocator =
                new InMemoryCapacityAllocator(
                        leaseCoordinator,
                        allocationStrategy,
                        allocationProperties
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

        InMemoryCapacityAllocator capacityAllocator =
                new InMemoryCapacityAllocator(
                        leaseCoordinator,
                        allocationStrategy,
                        allocationProperties
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