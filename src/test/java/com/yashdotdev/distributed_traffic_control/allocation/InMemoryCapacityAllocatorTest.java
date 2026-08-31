package com.yashdotdev.distributed_traffic_control.allocation;

import com.yashdotdev.distributed_traffic_control.lease.InMemoryLeaseCoordinator;
import com.yashdotdev.distributed_traffic_control.lease.QuotaLease;
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
    void shouldAllocateCapacityThroughLeaseCoordinator() {

        Clock clock = Clock.fixed(
                Instant.parse("2026-08-28T10:00:00Z"),
                ZoneOffset.UTC
        );

        InMemoryLeaseCoordinator leaseCoordinator =
                new InMemoryLeaseCoordinator(clock);

        InMemoryCapacityAllocator capacityAllocator =
                new InMemoryCapacityAllocator(
                        leaseCoordinator
                );

        QuotaKey quotaKey = createQuotaKey();

        leaseCoordinator.registerQuota(
                quotaKey,
                100
        );

        Optional<QuotaLease> lease =
                capacityAllocator.allocate(
                        quotaKey,
                        "node-1",
                        40,
                        Duration.ofSeconds(60)
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
