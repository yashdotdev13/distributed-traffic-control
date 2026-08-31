package com.yashdotdev.distributed_traffic_control.lease;

import com.yashdotdev.distributed_traffic_control.quota.QuotaKey;
import com.yashdotdev.distributed_traffic_control.traffic.TrafficSubject;
import com.yashdotdev.distributed_traffic_control.traffic.TrafficSubjectType;
import org.junit.jupiter.api.Test;

import java.time.*;
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


    @Test
    void shouldAllocateLeaseWhenRequestedCapacityEqualsRemainingCapacity() {

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
                        30,
                        Duration.ofSeconds(60)
                );

        assertTrue(firstLease.isPresent());
        assertTrue(secondLease.isPresent());

        assertEquals(
                30,
                secondLease.get().getAllocatedCapacity()
        );

        assertEquals(
                30,
                secondLease.get().getRemainingCapacity()
        );
    }

    @Test
    void shouldReclaimUnusedCapacityFromExpiredLease() {

        MutableClock clock = new MutableClock(
                Instant.parse("2026-08-28T10:00:00Z")
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
                        60,
                        Duration.ofSeconds(30)
                );

        assertTrue(firstLease.isPresent());

        QuotaLease quotaLease = firstLease.get();

        quotaLease.consume();
        quotaLease.consume();

        assertEquals(
                58,
                quotaLease.getRemainingCapacity()
        );

        clock.advanceSeconds(31);

        Optional<QuotaLease> secondLease =
                leaseCoordinator.acquireLease(
                        quotaKey,
                        "node-2",
                        98,
                        Duration.ofSeconds(30)
                );

        assertTrue(secondLease.isPresent());

        assertEquals(
                98,
                secondLease.get().getAllocatedCapacity()
        );
    }

    private static class MutableClock extends Clock {

        private Instant currentTime;

        private MutableClock(Instant currentTime) {
            this.currentTime = currentTime;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
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
            currentTime = currentTime.plusSeconds(seconds);
        }
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
