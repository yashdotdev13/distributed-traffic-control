package com.yashdotdev.distributed_traffic_control.lease;

import com.yashdotdev.distributed_traffic_control.quota.QuotaKey;
import com.yashdotdev.distributed_traffic_control.traffic.TrafficSubject;
import com.yashdotdev.distributed_traffic_control.traffic.TrafficSubjectType;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    void shouldConsumeCapacityFromActiveLease() {

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
                        10,
                        Duration.ofSeconds(60)
                );

        assertTrue(lease.isPresent());

        LeaseConsumptionResult firstResult =
                leaseCoordinator.tryConsume(
                        lease.get(),
                        clock.instant()
                );

        LeaseConsumptionResult secondResult =
                leaseCoordinator.tryConsume(
                        lease.get(),
                        clock.instant()
                );

        assertTrue(firstResult.isConsumed());
        assertEquals(9, firstResult.getRemainingCapacity());

        assertTrue(secondResult.isConsumed());
        assertEquals(8, secondResult.getRemainingCapacity());
    }


    @Test
    void shouldRejectConsumptionFromExpiredLease() {

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

        Optional<QuotaLease> lease =
                leaseCoordinator.acquireLease(
                        quotaKey,
                        "node-1",
                        10,
                        Duration.ofSeconds(30)
                );

        assertTrue(lease.isPresent());

        clock.advanceSeconds(31);

        LeaseConsumptionResult result =
                leaseCoordinator.tryConsume(
                        lease.get(),
                        clock.instant()
                );

        assertFalse(result.isConsumed());

        assertEquals(
                10,
                result.getRemainingCapacity()
        );
    }

    @Test
    void shouldRejectConsumptionWhenLeaseCapacityIsExhausted() {

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
                        2,
                        Duration.ofSeconds(60)
                );

        assertTrue(lease.isPresent());

        LeaseConsumptionResult firstResult =
                leaseCoordinator.tryConsume(
                        lease.get(),
                        clock.instant()
                );

        LeaseConsumptionResult secondResult =
                leaseCoordinator.tryConsume(
                        lease.get(),
                        clock.instant()
                );

        LeaseConsumptionResult thirdResult =
                leaseCoordinator.tryConsume(
                        lease.get(),
                        clock.instant()
                );

        assertTrue(firstResult.isConsumed());
        assertEquals(1, firstResult.getRemainingCapacity());

        assertTrue(secondResult.isConsumed());
        assertEquals(0, secondResult.getRemainingCapacity());

        assertFalse(thirdResult.isConsumed());
        assertEquals(0, thirdResult.getRemainingCapacity());
    }

    @Test
    void shouldRenewActiveLease() {

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

        Optional<QuotaLease> lease =
                leaseCoordinator.acquireLease(
                        quotaKey,
                        "node-1",
                        20,
                        Duration.ofSeconds(30)
                );

        assertTrue(lease.isPresent());

        QuotaLease quotaLease = lease.get();

        assertEquals(
                Instant.parse("2026-08-28T10:00:30Z"),
                quotaLease.getExpiresAt()
        );

        boolean renewed =
                leaseCoordinator.renewLease(
                        quotaLease,
                        Duration.ofSeconds(30)
                );

        assertTrue(renewed);

        assertEquals(
                Instant.parse("2026-08-28T10:01:00Z"),
                quotaLease.getExpiresAt()
        );
    }

    @Test
    void shouldRejectRenewalOfExpiredLease() {

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

        Optional<QuotaLease> lease =
                leaseCoordinator.acquireLease(
                        quotaKey,
                        "node-1",
                        20,
                        Duration.ofSeconds(30)
                );

        assertTrue(lease.isPresent());

        QuotaLease quotaLease = lease.get();

        clock.advanceSeconds(31);

        boolean renewed =
                leaseCoordinator.renewLease(
                        quotaLease,
                        Duration.ofSeconds(30)
                );

        assertFalse(renewed);

        assertEquals(
                Instant.parse("2026-08-28T10:00:30Z"),
                quotaLease.getExpiresAt()
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
