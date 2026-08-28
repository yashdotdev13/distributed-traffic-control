package com.yashdotdev.distributed_traffic_control.lease;

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

class InMemoryLeaseCoordinatorTest {

    @Test
    void shouldAcquireLeaseWhenCapacityIsAvailable() {

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
                        "node-1",
                        30,
                        Duration.ofSeconds(60)
                );

        assertTrue(lease.isPresent());

        QuotaLease quotaLease = lease.get();

        assertEquals(
                "node-1",
                quotaLease.getNodeId()
        );

        assertEquals(
                30,
                quotaLease.getAllocatedCapacity()
        );

        assertEquals(
                30,
                quotaLease.getRemainingCapacity()
        );

        assertEquals(
                Instant.parse("2026-08-28T10:00:00Z"),
                quotaLease.getIssuedAt()
        );

        assertEquals(
                Instant.parse("2026-08-28T10:01:00Z"),
                quotaLease.getExpiresAt()
        );
    }

    @Test
    void shouldNotAllocateMoreThanAvailableGlobalCapacity() {

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

        Optional<QuotaLease> firstLease =
                leaseCoordinator.acquireLease(
                        quotaKey,
                        "node-1",
                        70,
                        Duration.ofSeconds(60)
                );

        Optional<QuotaLease> secondLease =
                leaseCoordinator.acquireLease(
                        quotaKey,
                        "node-2",
                        40,
                        Duration.ofSeconds(60)
                );

        assertTrue(firstLease.isPresent());
        assertTrue(secondLease.isEmpty());
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
