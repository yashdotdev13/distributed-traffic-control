package com.yashdotdev.distributed_traffic_control.lease;

import com.yashdotdev.distributed_traffic_control.quota.QuotaKey;
import com.yashdotdev.distributed_traffic_control.traffic.TrafficSubject;
import com.yashdotdev.distributed_traffic_control.traffic.TrafficSubjectType;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

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
                        "node-1",
                        clock.instant()
                );

        LeaseConsumptionResult secondResult =
                leaseCoordinator.tryConsume(
                        lease.get(),
                        "node-1",
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
                        "node-1",
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
                        "node-1",
                        clock.instant()
                );

        LeaseConsumptionResult secondResult =
                leaseCoordinator.tryConsume(
                        lease.get(),
                        "node-1",
                        clock.instant()
                );

        LeaseConsumptionResult thirdResult =
                leaseCoordinator.tryConsume(
                        lease.get(),
                        "node-1",
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
                        "node-1",
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
                        "node-1",
                        Duration.ofSeconds(30)
                );

        assertFalse(renewed);

        assertEquals(
                Instant.parse("2026-08-28T10:00:30Z"),
                quotaLease.getExpiresAt()
        );
    }

    @Test
    void shouldNotReclaimRenewedLeaseBeforeNewExpiration() {

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
                        60,
                        Duration.ofSeconds(30)
                );

        assertTrue(lease.isPresent());

        QuotaLease quotaLease = lease.get();

        clock.advanceSeconds(20);

        boolean renewed =
                leaseCoordinator.renewLease(
                        quotaLease,
                        "node-1",
                        Duration.ofSeconds(30)
                );

        assertTrue(renewed);

        assertEquals(
                Instant.parse("2026-08-28T10:01:00Z"),
                quotaLease.getExpiresAt()
        );

        clock.advanceSeconds(15);

        Optional<QuotaLease> secondLease =
                leaseCoordinator.acquireLease(
                        quotaKey,
                        "node-2",
                        41,
                        Duration.ofSeconds(30)
                );

        assertTrue(secondLease.isEmpty());
    }


    @Test
    void shouldReleaseUnusedCapacityFromActiveLease() {

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
                        60,
                        Duration.ofSeconds(60)
                );

        assertTrue(lease.isPresent());

        QuotaLease quotaLease = lease.get();

        LeaseConsumptionResult firstResult =
                leaseCoordinator.tryConsume(
                        quotaLease,
                        "node-1",
                        clock.instant()
                );

        LeaseConsumptionResult secondResult =
                leaseCoordinator.tryConsume(
                        quotaLease,
                        "node-1",
                        clock.instant()
                );

        assertTrue(firstResult.isConsumed());
        assertTrue(secondResult.isConsumed());
        assertEquals(58, secondResult.getRemainingCapacity());

        boolean released =
                leaseCoordinator.releaseLease(quotaLease);

        assertTrue(released);

        Optional<QuotaLease> secondLease =
                leaseCoordinator.acquireLease(
                        quotaKey,
                        "node-2",
                        98,
                        Duration.ofSeconds(60)
                );

        assertTrue(secondLease.isPresent());
        assertEquals(
                98,
                secondLease.get().getAllocatedCapacity()
        );
    }

    @Test
    void shouldNotReleaseLeaseMoreThanOnce() {

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
                        60,
                        Duration.ofSeconds(60)
                );

        assertTrue(lease.isPresent());

        QuotaLease quotaLease = lease.get();

        assertTrue(
                leaseCoordinator.releaseLease(quotaLease)
        );

        assertFalse(
                leaseCoordinator.releaseLease(quotaLease)
        );
    }

    @Test
    void shouldNotConsumeReleasedLease() {

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
                        20,
                        Duration.ofSeconds(60)
                );

        assertTrue(lease.isPresent());

        QuotaLease quotaLease = lease.get();

        assertTrue(
                leaseCoordinator.releaseLease(quotaLease)
        );

        LeaseConsumptionResult result =
                leaseCoordinator.tryConsume(
                        quotaLease,
                        "node-1",
                        clock.instant()
                );
        assertFalse(result.isConsumed());
    }


    @Test
    void shouldReturnUnusedCapacityWhenLeaseIsReleased() {

        Clock clock = Clock.fixed(
                Instant.parse("2026-08-28T10:00:00Z"),
                ZoneOffset.UTC
        );
        InMemoryLeaseCoordinator leaseCoordinator =
                new InMemoryLeaseCoordinator(clock);

        QuotaKey quotaKey = createQuotaKey();

        leaseCoordinator.registerQuota(
                quotaKey,
                10
        );

        Optional<QuotaLease> lease =
                leaseCoordinator.acquireLease(
                        quotaKey,
                        "node-1",
                        10,
                        Duration.ofSeconds(60)
                );

        assertTrue(lease.isPresent());

        LeaseConsumptionResult first =
                leaseCoordinator.tryConsume(
                        lease.get(),
                        "node-1",
                        clock.instant()
                );

        LeaseConsumptionResult second =
                leaseCoordinator.tryConsume(
                        lease.get(),
                        "node-1",
                        clock.instant()
                );

        LeaseConsumptionResult third =
                leaseCoordinator.tryConsume(
                        lease.get(),
                        "node-1",
                        clock.instant()
                );

        assertTrue(first.isConsumed());
        assertTrue(second.isConsumed());
        assertTrue(third.isConsumed());

        assertEquals(
                7,
                lease.get().getRemainingCapacity()
        );

        assertTrue(
                leaseCoordinator.releaseLease(
                        lease.get()
                )
        );

        Optional<QuotaLease> replacementLease =
                leaseCoordinator.acquireLease(
                        quotaKey,
                        "node-2",
                        7,
                        Duration.ofSeconds(60)
                );

        assertTrue(replacementLease.isPresent());

        assertEquals(
                7,
                replacementLease.get().getAllocatedCapacity()
        );

        assertEquals(
                7,
                replacementLease.get().getRemainingCapacity()
        );
    }

    @Test
    void shouldPreventMultipleNodesFromExceedingGlobalCapacity() {

        Clock clock = Clock.fixed(
                Instant.parse("2026-08-28T10:00:00Z"),
                ZoneOffset.UTC
        );
        InMemoryLeaseCoordinator leaseCoordinator =
                new InMemoryLeaseCoordinator(clock);

        QuotaKey quotaKey = createQuotaKey();

        leaseCoordinator.registerQuota(
                quotaKey,
                10
        );

        Optional<QuotaLease> nodeALease =
                leaseCoordinator.acquireLease(
                        quotaKey,
                        "node-a",
                        6,
                        Duration.ofSeconds(60)
                );

        Optional<QuotaLease> nodeBLease =
                leaseCoordinator.acquireLease(
                        quotaKey,
                        "node-b",
                        4,
                        Duration.ofSeconds(60)
                );

        assertTrue(nodeALease.isPresent());
        assertTrue(nodeBLease.isPresent());

        assertEquals(
                "node-a",
                nodeALease.get().getNodeId()
        );

        assertEquals(
                "node-b",
                nodeBLease.get().getNodeId()
        );

        assertEquals(
                6,
                nodeALease.get().getAllocatedCapacity()
        );

        assertEquals(
                4,
                nodeBLease.get().getAllocatedCapacity()
        );

        Optional<QuotaLease> rejectedLease =
                leaseCoordinator.acquireLease(
                        quotaKey,
                        "node-a",
                        1,
                        Duration.ofSeconds(60)
                );

        assertTrue(
                rejectedLease.isEmpty()
        );
    }

    @Test
    void shouldNotExceedGlobalCapacityUnderConcurrentAllocation()
            throws Exception {

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

        int numberOfNodes = 20;
        long requestedCapacity = 10;

        ExecutorService executorService =
                Executors.newFixedThreadPool(numberOfNodes);

        CountDownLatch startLatch =
                new CountDownLatch(1);

        List<Future<Optional<QuotaLease>>> futures =
                new ArrayList<>();

        for (int i = 0; i < numberOfNodes; i++) {

            String nodeId = "node-" + i;

            futures.add(
                    executorService.submit(() -> {

                        startLatch.await();

                        return leaseCoordinator.acquireLease(
                                quotaKey,
                                nodeId,
                                requestedCapacity,
                                Duration.ofSeconds(60)
                        );
                    })
            );
        }

        startLatch.countDown();

        int successfulAllocations = 0;
        long totalAllocatedCapacity = 0;

        for (Future<Optional<QuotaLease>> future : futures) {

            Optional<QuotaLease> lease =
                    future.get();

            if (lease.isPresent()) {
                successfulAllocations++;

                totalAllocatedCapacity +=
                        lease.get().getAllocatedCapacity();
            }
        }

        executorService.shutdown();

        assertTrue(
                executorService.awaitTermination(
                        5,
                        TimeUnit.SECONDS
                )
        );

        assertEquals(
                10,
                successfulAllocations
        );

        assertEquals(
                100,
                totalAllocatedCapacity
        );
    }

    @Test
    void shouldNotExceedLeaseCapacityUnderConcurrentConsumption()
            throws Exception {

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
                        100,
                        Duration.ofSeconds(60)
                );

        assertTrue(lease.isPresent());

        QuotaLease quotaLease = lease.get();

        int numberOfConsumers = 120;

        ExecutorService executorService =
                Executors.newFixedThreadPool(20);

        CountDownLatch startLatch =
                new CountDownLatch(1);

        List<Future<LeaseConsumptionResult>> futures =
                new ArrayList<>();

        for (int i = 0; i < numberOfConsumers; i++) {

            futures.add(
                    executorService.submit(() -> {

                        startLatch.await();

                        return leaseCoordinator.tryConsume(
                                quotaLease,
                                "node-1",
                                clock.instant()
                        );
                    })
            );
        }

        startLatch.countDown();

        int successfulConsumptions = 0;
        int rejectedConsumptions = 0;

        for (Future<LeaseConsumptionResult> future : futures) {

            LeaseConsumptionResult result =
                    future.get();

            if (result.isConsumed()) {
                successfulConsumptions++;
            } else {
                rejectedConsumptions++;
            }
        }

        executorService.shutdown();

        assertTrue(
                executorService.awaitTermination(
                        5,
                        TimeUnit.SECONDS
                )
        );

        assertEquals(
                100,
                successfulConsumptions
        );

        assertEquals(
                20,
                rejectedConsumptions
        );

        assertEquals(
                0,
                quotaLease.getRemainingCapacity()
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

        assertEquals(
                19,
                quotaLease.getRemainingCapacity()
        );
    }

    @Test
    void shouldRejectRenewalFromDifferentNode() {

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
                Instant.parse("2026-08-28T10:01:00Z"),
                quotaLease.getExpiresAt()
        );

        boolean renewedByOwner =
                leaseCoordinator.renewLease(
                        quotaLease,
                        "node-a",
                        Duration.ofSeconds(30)
                );

        assertTrue(renewedByOwner);

        assertEquals(
                Instant.parse("2026-08-28T10:01:30Z"),
                quotaLease.getExpiresAt()
        );

        boolean renewedByOtherNode =
                leaseCoordinator.renewLease(
                        quotaLease,
                        "node-b",
                        Duration.ofSeconds(30)
                );

        assertFalse(renewedByOtherNode);

        assertEquals(
                Instant.parse("2026-08-28T10:01:30Z"),
                quotaLease.getExpiresAt()
        );
    }

    @Test
    void shouldRegisterQuota() {

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
                        100,
                        Duration.ofSeconds(60)
                );

        assertTrue(lease.isPresent());

        assertEquals(
                100,
                lease.get().getAllocatedCapacity()
        );

        assertEquals(
                100,
                lease.get().getRemainingCapacity()
        );
    }

    @Test
    void shouldRemoveQuotaAndPreventNewLeases() {

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

        boolean removed =
                leaseCoordinator.removeQuota(
                        quotaKey
                );

        assertTrue(removed);

        Optional<QuotaLease> lease =
                leaseCoordinator.acquireLease(
                        quotaKey,
                        "node-1",
                        10,
                        Duration.ofSeconds(60)
                );

        assertTrue(lease.isEmpty());
    }

    @Test
    void shouldShareGlobalCapacityAcrossNodes() {

        Clock clock = Clock.fixed(
                Instant.parse("2026-08-28T10:00:00Z"),
                ZoneOffset.UTC
        );

        InMemoryLeaseCoordinator leaseCoordinator =
                new InMemoryLeaseCoordinator(clock);

        QuotaKey quotaKey = createQuotaKey();

        leaseCoordinator.registerQuota(
                quotaKey,
                10
        );

        Optional<QuotaLease> nodeALease =
                leaseCoordinator.acquireLease(
                        quotaKey,
                        "node-a",
                        6,
                        Duration.ofSeconds(60)
                );

        assertTrue(nodeALease.isPresent());

        assertEquals(
                6,
                nodeALease.get().getAllocatedCapacity()
        );

        assertEquals(
                6,
                nodeALease.get().getRemainingCapacity()
        );

        Optional<QuotaLease> nodeBLease =
                leaseCoordinator.acquireLease(
                        quotaKey,
                        "node-b",
                        4,
                        Duration.ofSeconds(60)
                );

        assertTrue(nodeBLease.isPresent());

        assertEquals(
                4,
                nodeBLease.get().getAllocatedCapacity()
        );

        assertEquals(
                4,
                nodeBLease.get().getRemainingCapacity()
        );

        Optional<QuotaLease> nodeCLease =
                leaseCoordinator.acquireLease(
                        quotaKey,
                        "node-c",
                        1,
                        Duration.ofSeconds(60)
                );

        assertTrue(nodeCLease.isEmpty());
    }

    @Test
    void shouldNotExceedGlobalCapacityWhenNodesAllocateConcurrently()
            throws Exception {

        Clock clock = Clock.fixed(
                Instant.parse("2026-08-28T10:00:00Z"),
                ZoneOffset.UTC
        );

        InMemoryLeaseCoordinator leaseCoordinator =
                new InMemoryLeaseCoordinator(clock);

        QuotaKey quotaKey = createQuotaKey();

        int globalCapacity = 100;
        int numberOfNodes = 20;
        int requestedCapacityPerNode = 10;

        leaseCoordinator.registerQuota(
                quotaKey,
                globalCapacity
        );

        ExecutorService executorService =
                Executors.newFixedThreadPool(numberOfNodes);

        CountDownLatch startLatch =
                new CountDownLatch(1);

        List<Future<Optional<QuotaLease>>> futures =
                new ArrayList<>();

        for (int i = 0; i < numberOfNodes; i++) {

            String nodeId = "node-" + i;

            futures.add(
                    executorService.submit(() -> {

                        startLatch.await();

                        return leaseCoordinator.acquireLease(
                                quotaKey,
                                nodeId,
                                requestedCapacityPerNode,
                                Duration.ofSeconds(60)
                        );
                    })
            );
        }

        startLatch.countDown();

        int successfulLeases = 0;
        long totalAllocatedCapacity = 0;

        for (Future<Optional<QuotaLease>> future : futures) {

            Optional<QuotaLease> lease =
                    future.get();

            if (lease.isPresent()) {
                successfulLeases++;

                totalAllocatedCapacity +=
                        lease.get().getAllocatedCapacity();
            }
        }

        executorService.shutdown();

        assertEquals(
                globalCapacity,
                totalAllocatedCapacity
        );

        assertEquals(
                globalCapacity / requestedCapacityPerNode,
                successfulLeases
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
