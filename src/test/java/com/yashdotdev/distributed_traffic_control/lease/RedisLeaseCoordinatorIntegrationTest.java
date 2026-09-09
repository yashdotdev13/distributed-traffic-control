package com.yashdotdev.distributed_traffic_control.lease;

import com.yashdotdev.distributed_traffic_control.quota.QuotaKey;
import com.yashdotdev.distributed_traffic_control.traffic.TrafficSubject;
import com.yashdotdev.distributed_traffic_control.traffic.TrafficSubjectType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class RedisLeaseCoordinatorIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Test
    void shouldNotExceedGlobalCapacityWhenNodesAllocateConcurrently() throws Exception {

        StringRedisTemplate redisTemplate = createRedisTemplate();
        RedisLeaseCoordinator leaseCoordinator = new RedisLeaseCoordinator(redisTemplate, Clock.fixed(Instant.parse("2026-08-28T10:00:00Z"), ZoneOffset.UTC));
        QuotaKey quotaKey = createQuotaKey();

        int globalCapacity = 100;
        int numberOfNodes = 20;
        int requestedCapacityPerNode = 10;

        leaseCoordinator.registerCapacity(new GlobalCapacityKey(quotaKey.getPolicyId(), quotaKey.getResources()), 100);
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfNodes);

        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Optional<QuotaLease>>> futures = new ArrayList<>();
        for (int i = 0; i < numberOfNodes; i++) {
            String nodeId = "node-" + i;
            futures.add(executorService.submit(() -> {
                startLatch.await();
                return leaseCoordinator.acquireLease(quotaKey, nodeId, requestedCapacityPerNode, Duration.ofSeconds(60));
            }));
        }
        startLatch.countDown();

        int successfulLeases = 0;
        long totalAllocatedCapacity = 0;
        for (Future<Optional<QuotaLease>> future : futures) {
            Optional<QuotaLease> lease = future.get();
            if (lease.isPresent()) {
                successfulLeases++;
                totalAllocatedCapacity += lease.get().getAllocatedCapacity();
            }
        }

        executorService.shutdown();
        assertEquals(globalCapacity, totalAllocatedCapacity);
        assertEquals(globalCapacity / requestedCapacityPerNode, successfulLeases);
    }

    @Test
    void shouldNotAcquireLeaseAfterQuotaIsRemoved() {

        StringRedisTemplate redisTemplate = createRedisTemplate();
        RedisLeaseCoordinator leaseCoordinator = new RedisLeaseCoordinator(redisTemplate);
        QuotaKey quotaKey = createQuotaKey();
        leaseCoordinator.registerCapacity(new GlobalCapacityKey(quotaKey.getPolicyId(), quotaKey.getResources()), 100);
        assertTrue(leaseCoordinator.removeCapacity(new GlobalCapacityKey(quotaKey.getPolicyId(), quotaKey.getResources())));
        Optional<QuotaLease> lease = leaseCoordinator.acquireLease(quotaKey, "node-1", 10, Duration.ofSeconds(60));
        assertTrue(lease.isEmpty());
    }

    @Test
    void shouldConsumeCapacityFromRedisLease() {

        StringRedisTemplate redisTemplate = createRedisTemplate();
        RedisLeaseCoordinator leaseCoordinator = new RedisLeaseCoordinator(redisTemplate);
        QuotaKey quotaKey = createQuotaKey();
        leaseCoordinator.registerCapacity(new GlobalCapacityKey(quotaKey.getPolicyId(), quotaKey.getResources()), 100);
        Optional<QuotaLease> lease = leaseCoordinator.acquireLease(quotaKey, "node-a", 20, Duration.ofSeconds(60));
        assertTrue(lease.isPresent());
        LeaseConsumptionResult result = leaseCoordinator.tryConsume(lease.get(), "node-a", Instant.now());
        assertTrue(result.isConsumed());
        assertEquals(19, result.getRemainingCapacity());
    }

    @Test
    void shouldRejectConsumptionFromDifferentNode() {
        StringRedisTemplate redisTemplate = createRedisTemplate();
        RedisLeaseCoordinator leaseCoordinator = new RedisLeaseCoordinator(redisTemplate);
        QuotaKey quotaKey = createQuotaKey();
        leaseCoordinator.registerCapacity(new GlobalCapacityKey(quotaKey.getPolicyId(), quotaKey.getResources()), 100);
        Optional<QuotaLease> lease = leaseCoordinator.acquireLease(quotaKey, "node-a", 20, Duration.ofSeconds(60));
        assertTrue(lease.isPresent());
        LeaseConsumptionResult result = leaseCoordinator.tryConsume(lease.get(), "node-b", Instant.now());
        assertFalse(result.isConsumed());
        assertEquals(20, result.getRemainingCapacity());
        assertEquals(20, lease.get().getRemainingCapacity());
    }

    @Test
    void shouldRenewLeaseForOwner() {

        StringRedisTemplate redisTemplate = createRedisTemplate();
        RedisLeaseCoordinator leaseCoordinator = new RedisLeaseCoordinator(redisTemplate, Clock.fixed(Instant.parse("2026-08-28T10:00:00Z"), ZoneOffset.UTC));
        QuotaKey quotaKey = createQuotaKey();
        leaseCoordinator.registerCapacity(new GlobalCapacityKey(quotaKey.getPolicyId(), quotaKey.getResources()), 100);
        Optional<QuotaLease> lease = leaseCoordinator.acquireLease(quotaKey, "node-a", 20, Duration.ofSeconds(60));
        assertTrue(lease.isPresent());
        QuotaLease quotaLease = lease.get();
        assertEquals(Instant.parse("2026-08-28T10:01:00Z"), quotaLease.getExpiresAt());
        boolean renewed = leaseCoordinator.renewLease(quotaLease, "node-a", Duration.ofSeconds(30));
        assertTrue(renewed);
        assertEquals(Instant.parse("2026-08-28T10:01:30Z"), quotaLease.getExpiresAt());
    }

    @Test
    void shouldRejectRenewalFromDifferentNode() {

        StringRedisTemplate redisTemplate = createRedisTemplate();
        RedisLeaseCoordinator leaseCoordinator = new RedisLeaseCoordinator(redisTemplate, Clock.fixed(Instant.parse("2026-08-28T10:00:00Z"), ZoneOffset.UTC));
        QuotaKey quotaKey = createQuotaKey();
        leaseCoordinator.registerCapacity(new GlobalCapacityKey(quotaKey.getPolicyId(), quotaKey.getResources()), 100);
        Optional<QuotaLease> lease = leaseCoordinator.acquireLease(quotaKey, "node-a", 20, Duration.ofSeconds(60));
        assertTrue(lease.isPresent());
        QuotaLease quotaLease = lease.get();
        assertEquals(Instant.parse("2026-08-28T10:01:00Z"), quotaLease.getExpiresAt());
        boolean renewedByOwner = leaseCoordinator.renewLease(quotaLease, "node-a", Duration.ofSeconds(30));
        assertTrue(renewedByOwner);
        assertEquals(Instant.parse("2026-08-28T10:01:30Z"), quotaLease.getExpiresAt());
        boolean renewedByOtherNode = leaseCoordinator.renewLease(quotaLease, "node-b", Duration.ofSeconds(30));
        assertFalse(renewedByOtherNode);
        assertEquals(Instant.parse("2026-08-28T10:01:30Z"), quotaLease.getExpiresAt());
    }

    private StringRedisTemplate createRedisTemplate() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379));
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
        return template;
    }

    @Test
    void shouldReleaseLeaseAndReturnUnusedCapacity() {
        StringRedisTemplate redisTemplate = createRedisTemplate();
        RedisLeaseCoordinator leaseCoordinator = new RedisLeaseCoordinator(redisTemplate);
        QuotaKey quotaKey = createQuotaKey();
        leaseCoordinator.registerCapacity(new GlobalCapacityKey(quotaKey.getPolicyId(), quotaKey.getResources()), 100);
        Optional<QuotaLease> lease = leaseCoordinator.acquireLease(quotaKey, "node-a", 20, Duration.ofSeconds(60));
        assertTrue(lease.isPresent());
        LeaseConsumptionResult consumption = leaseCoordinator.tryConsume(lease.get(), "node-a", Instant.now());
        assertTrue(consumption.isConsumed());
        assertEquals(19, consumption.getRemainingCapacity());
        boolean released = leaseCoordinator.releaseLease(lease.get());
        assertTrue(released);
        Optional<QuotaLease> replacementLease = leaseCoordinator.acquireLease(quotaKey, "node-b", 99, Duration.ofSeconds(60));
        assertTrue(replacementLease.isPresent());
        assertEquals(99, replacementLease.get().getAllocatedCapacity());
    }

    @Test
    void shouldReclaimCapacityFromExpiredLease() {

        MutableClock clock = new MutableClock(Instant.parse("2026-08-28T10:00:00Z"));
        StringRedisTemplate redisTemplate = createRedisTemplate();
        RedisLeaseCoordinator leaseCoordinator = new RedisLeaseCoordinator(redisTemplate, clock);
        QuotaKey quotaKey = createQuotaKey();
        GlobalCapacityKey capacityKey = new GlobalCapacityKey(quotaKey.getPolicyId(), quotaKey.getResources());
        leaseCoordinator.registerCapacity(capacityKey, 100);

        /*
         * Node A acquires 20 units.
         */
        Optional<QuotaLease> firstLease = leaseCoordinator.acquireLease(quotaKey, "node-a", 20, Duration.ofSeconds(30));
        assertTrue(firstLease.isPresent());
        assertEquals(20, firstLease.get().getAllocatedCapacity());
        assertEquals(20, firstLease.get().getRemainingCapacity());

        /*
         * 30 seconds have now passed.
         * The lease is expired from the coordinator's
         * logical point of view.
         */
        clock.advanceSeconds(31);
        /*
         * Node B requests the entire global capacity.
         *
         * The coordinator must first reclaim the unused
         * 20 units from node A's expired lease and then
         * allocate the full 100 units to node B.
         */
        Optional<QuotaLease> replacementLease = leaseCoordinator.acquireLease(quotaKey, "node-b", 100, Duration.ofSeconds(30));
        assertTrue(replacementLease.isPresent());
        assertEquals(100, replacementLease.get().getAllocatedCapacity());
        assertEquals(100, replacementLease.get().getRemainingCapacity());
        assertEquals("node-b", replacementLease.get().getNodeId());
    }


    @BeforeEach
    void cleanRedis() {
        StringRedisTemplate redisTemplate = createRedisTemplate();
        Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection().serverCommands().flushDb();
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
        private void advanceSeconds(long seconds) {
            currentTime = currentTime.plusSeconds(seconds);
        }
    }


    private QuotaKey createQuotaKey() {
        String uniqueId = UUID.randomUUID().toString();
        return new QuotaKey("redis-test-policy-" + uniqueId, new TrafficSubject("redis-test-user-" + uniqueId, TrafficSubjectType.USER), "/api/orders");
    }
}