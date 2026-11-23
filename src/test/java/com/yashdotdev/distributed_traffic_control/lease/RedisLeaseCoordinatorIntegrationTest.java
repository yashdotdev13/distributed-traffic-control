package com.yashdotdev.distributed_traffic_control.lease;

import com.yashdotdev.distributed_traffic_control.config.RedisKeyBuilder;
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

    @Test
    void shouldRefillGlobalCapacityOverTime() throws InterruptedException {
        RedisLeaseCoordinator coordinator = new RedisLeaseCoordinator(createRedisTemplate());
        GlobalCapacityKey capacityKey = new GlobalCapacityKey("refill-policy", "/api/orders");
        for (int i = 0; i < 5; i++) {
            long remaining = coordinator.tryConsume(capacityKey, 5, 1, Instant.now());
            assertTrue(remaining >= 0, "Expected the initial bucket to allow 5 requests");
        }
        assertEquals(-1L, coordinator.tryConsume(capacityKey, 5, 1,
                Instant.now()), "Expected the bucket to be exhausted");
        Thread.sleep(1100);
        assertTrue(coordinator.tryConsume(capacityKey, 5, 1,
                Instant.now()) >= 0, "Expected a refilled token to be available");
    }

    @Test
    void shouldShareTokenBucketAcrossCoordinatorInstances() {
        RedisLeaseCoordinator firstCoordinator = new RedisLeaseCoordinator(createRedisTemplate());
        RedisLeaseCoordinator secondCoordinator = new RedisLeaseCoordinator(createRedisTemplate());

        GlobalCapacityKey capacityKey = new GlobalCapacityKey("shared-token-bucket-policy", "/api/shared-test");
        for (int i = 0; i < 3; i++) {
            assertTrue(firstCoordinator.tryConsume(capacityKey, 5, 1, Instant.now()) >= 0,
                    "First coordinator should consume available tokens");
        }
        for (int i = 0; i < 2; i++) {
            assertTrue(secondCoordinator.tryConsume(capacityKey, 5, 1, Instant.now()) >= 0, "Second coordinator should consume the shared tokens");
        }
        assertEquals(-1L, firstCoordinator.tryConsume(capacityKey, 5, 1, Instant.now()));
        assertEquals(-1L, secondCoordinator.tryConsume(capacityKey, 5, 1, Instant.now()));
    }

    @Test
    void shouldPreserveExactDefaultKeyFormatsForBackwardCompatibility() {
        StringRedisTemplate redisTemplate = createRedisTemplate();
        // Construct coordinator using legacy default constructor
        RedisLeaseCoordinator coordinator = new RedisLeaseCoordinator(redisTemplate);

        String policyId = "compat-policy-" + UUID.randomUUID();
        String resource = "/api/v1/orders";
        GlobalCapacityKey capacityKey = new GlobalCapacityKey(policyId, resource);
        QuotaKey quotaKey = new QuotaKey(policyId, new TrafficSubject("test-user", TrafficSubjectType.USER), resource);

        coordinator.registerCapacity(capacityKey, 50);

        // Verify exact legacy global capacity key format: traffic-control:quota:<policyId>:<resource>
        String expectedGlobalKey = "traffic-control:quota:" + policyId + ":" + resource;
        assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey(expectedGlobalKey)),
                "Legacy global capacity key must exist in Redis");
        assertEquals("50", redisTemplate.opsForValue().get(expectedGlobalKey));

        // Acquire lease and verify exact legacy lease key format: traffic-control:lease:<leaseId>
        Optional<QuotaLease> acquiredLease = coordinator.acquireLease(quotaKey, "node-compat", 10, Duration.ofSeconds(60));
        assertTrue(acquiredLease.isPresent());
        String leaseId = acquiredLease.get().getLeaseId();

        String expectedLeaseKey = "traffic-control:lease:" + leaseId;
        assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey(expectedLeaseKey)),
                "Legacy lease key must exist in Redis");

        // Verify exact legacy registry key format: traffic-control:quota:<policyId>:<resource>:leases
        String expectedRegistryKey = expectedGlobalKey + ":leases";
        assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey(expectedRegistryKey)),
                "Legacy lease registry key must exist in Redis");

        Set<String> registryMembers = redisTemplate.opsForZSet().range(expectedRegistryKey, 0, -1);
        assertNotNull(registryMembers);
        assertTrue(registryMembers.contains(expectedLeaseKey),
                "Lease registry must contain exact legacy namespaced lease key");
    }

    @Test
    void shouldIsolateCapacityBetweenDifferentNamespaces() {
        StringRedisTemplate redisTemplate = createRedisTemplate();

        RedisKeyBuilder builderA = new RedisKeyBuilder("dev:traffic-control");
        RedisKeyBuilder builderB = new RedisKeyBuilder("prod:traffic-control");

        RedisLeaseCoordinator coordinatorA = new RedisLeaseCoordinator(redisTemplate, builderA);
        RedisLeaseCoordinator coordinatorB = new RedisLeaseCoordinator(redisTemplate, builderB);

        String sharedPolicyId = "shared-policy-" + UUID.randomUUID();
        String sharedResource = "/api/checkout";
        GlobalCapacityKey capacityKey = new GlobalCapacityKey(sharedPolicyId, sharedResource);
        QuotaKey quotaKey = new QuotaKey(sharedPolicyId, new TrafficSubject("user-1", TrafficSubjectType.USER), sharedResource);

        // Register capacity in namespace A only
        coordinatorA.registerCapacity(capacityKey, 50);

        String keyA = "dev:traffic-control:quota:" + sharedPolicyId + ":" + sharedResource;
        String keyB = "prod:traffic-control:quota:" + sharedPolicyId + ":" + sharedResource;

        assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey(keyA)));
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(keyB)));

        // Coordinator B cannot acquire lease because capacity is not registered in namespace B
        Optional<QuotaLease> leaseBAttempt = coordinatorB.acquireLease(quotaKey, "node-b", 10, Duration.ofSeconds(60));
        assertTrue(leaseBAttempt.isEmpty(), "Namespace B must not read or acquire capacity from namespace A");

        // Register separate capacity in namespace B
        coordinatorB.registerCapacity(capacityKey, 100);
        assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey(keyB)));
        assertEquals("50", redisTemplate.opsForValue().get(keyA));
        assertEquals("100", redisTemplate.opsForValue().get(keyB));

        // Coordinator A acquires 30 units: namespace A capacity becomes 20, namespace B remains 100
        Optional<QuotaLease> leaseA = coordinatorA.acquireLease(quotaKey, "node-a", 30, Duration.ofSeconds(60));
        assertTrue(leaseA.isPresent());
        assertEquals("20", redisTemplate.opsForValue().get(keyA));
        assertEquals("100", redisTemplate.opsForValue().get(keyB));

        // Coordinator B can still acquire full 100 units independently
        Optional<QuotaLease> leaseB = coordinatorB.acquireLease(quotaKey, "node-b", 100, Duration.ofSeconds(60));
        assertTrue(leaseB.isPresent());
        assertEquals("0", redisTemplate.opsForValue().get(keyB));
        assertEquals("20", redisTemplate.opsForValue().get(keyA));

        // Registry keys are isolated
        String registryKeyA = keyA + ":leases";
        String registryKeyB = keyB + ":leases";
        Set<String> membersA = redisTemplate.opsForZSet().range(registryKeyA, 0, -1);
        Set<String> membersB = redisTemplate.opsForZSet().range(registryKeyB, 0, -1);
        assertNotNull(membersA);
        assertNotNull(membersB);
        assertEquals(1, membersA.size());
        assertEquals(1, membersB.size());
        assertTrue(membersA.contains("dev:traffic-control:lease:" + leaseA.get().getLeaseId()));
        assertTrue(membersB.contains("prod:traffic-control:lease:" + leaseB.get().getLeaseId()));

        // Removing capacity in coordinator A does not affect coordinator B
        assertTrue(coordinatorA.removeCapacity(capacityKey));
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(keyA)));
        assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey(keyB)));
    }

    @Test
    void shouldIsolateLeaseOperationsAcrossNamespaces() {
        StringRedisTemplate redisTemplate = createRedisTemplate();

        RedisKeyBuilder builderA = new RedisKeyBuilder("dev:traffic-control");
        RedisKeyBuilder builderB = new RedisKeyBuilder("prod:traffic-control");

        RedisLeaseCoordinator coordinatorA = new RedisLeaseCoordinator(redisTemplate, builderA);
        RedisLeaseCoordinator coordinatorB = new RedisLeaseCoordinator(redisTemplate, builderB);

        String policyId = "lease-iso-policy-" + UUID.randomUUID();
        String resource = "/api/payments";
        GlobalCapacityKey capacityKey = new GlobalCapacityKey(policyId, resource);
        QuotaKey quotaKey = new QuotaKey(policyId, new TrafficSubject("user-2", TrafficSubjectType.USER), resource);

        coordinatorA.registerCapacity(capacityKey, 50);
        Optional<QuotaLease> leaseAOpt = coordinatorA.acquireLease(quotaKey, "node-shared", 20, Duration.ofSeconds(60));
        assertTrue(leaseAOpt.isPresent());
        QuotaLease leaseA = leaseAOpt.get();

        String namespacedLeaseKeyA = "dev:traffic-control:lease:" + leaseA.getLeaseId();
        assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey(namespacedLeaseKeyA)));

        // 1. Coordinator B cannot consume lease A
        LeaseConsumptionResult consumeResultB = coordinatorB.tryConsume(leaseA, "node-shared", Instant.now());
        assertFalse(consumeResultB.isConsumed(), "Coordinator B should not be able to consume lease from namespace A");
        assertEquals(20, leaseA.getRemainingCapacity());
        assertEquals("20", redisTemplate.opsForHash().get(namespacedLeaseKeyA, "remainingCapacity"));

        // 2. Coordinator B cannot renew lease A
        boolean renewedByB = coordinatorB.renewLease(leaseA, "node-shared", Duration.ofSeconds(30));
        assertFalse(renewedByB, "Coordinator B should not be able to renew lease from namespace A");

        // 3. Coordinator B cannot release lease A
        boolean releasedByB = coordinatorB.releaseLease(leaseA);
        assertFalse(releasedByB, "Coordinator B should not be able to release lease from namespace A");
        assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey(namespacedLeaseKeyA)),
                "Namespace A lease must not be deleted by namespace B");

        // 4. Coordinator A can consume, renew, and release its own lease
        LeaseConsumptionResult consumeResultA = coordinatorA.tryConsume(leaseA, "node-shared", Instant.now());
        assertTrue(consumeResultA.isConsumed());
        assertEquals(19, consumeResultA.getRemainingCapacity());
        assertEquals("19", redisTemplate.opsForHash().get(namespacedLeaseKeyA, "remainingCapacity"));

        boolean renewedByA = coordinatorA.renewLease(leaseA, "node-shared", Duration.ofSeconds(30));
        assertTrue(renewedByA);

        boolean releasedByA = coordinatorA.releaseLease(leaseA);
        assertTrue(releasedByA);
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(namespacedLeaseKeyA)));

        // Capacity restored in namespace A: 30 remaining + 19 returned unused = 49
        String keyA = "dev:traffic-control:quota:" + policyId + ":" + resource;
        assertEquals("49", redisTemplate.opsForValue().get(keyA));
    }

    @Test
    void shouldUseNamespacedKeysInAllLuaScripts() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-28T10:00:00Z"));
        StringRedisTemplate redisTemplate = createRedisTemplate();
        String namespace = "staging:traffic-control";
        RedisKeyBuilder builder = new RedisKeyBuilder(namespace);
        RedisLeaseCoordinator coordinator = new RedisLeaseCoordinator(redisTemplate, builder, clock);

        String policyId = "lua-policy-" + UUID.randomUUID();
        String resource = "/api/data";
        GlobalCapacityKey capacityKey = new GlobalCapacityKey(policyId, resource);
        QuotaKey quotaKey = new QuotaKey(policyId, new TrafficSubject("lua-user", TrafficSubjectType.USER), resource);

        coordinator.registerCapacity(capacityKey, 100);

        String expectedCapacityKey = namespace + ":quota:" + policyId + ":" + resource;
        String expectedRegistryKey = expectedCapacityKey + ":leases";

        // 1. Acquire script with namespaced keys
        Optional<QuotaLease> lease1Opt = coordinator.acquireLease(quotaKey, "node-1", 20, Duration.ofSeconds(30));
        assertTrue(lease1Opt.isPresent());
        QuotaLease lease1 = lease1Opt.get();
        String expectedLease1Key = namespace + ":lease:" + lease1.getLeaseId();

        // Verify Redis state created by acquire script
        assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey(expectedLease1Key)));
        assertEquals("node-1", redisTemplate.opsForHash().get(expectedLease1Key, "nodeId"));
        assertEquals("20", redisTemplate.opsForHash().get(expectedLease1Key, "allocatedCapacity"));
        assertEquals("20", redisTemplate.opsForHash().get(expectedLease1Key, "remainingCapacity"));
        assertEquals("80", redisTemplate.opsForValue().get(expectedCapacityKey));

        // Verify sorted set registry entry has namespaced lease key
        Set<String> registry = redisTemplate.opsForZSet().range(expectedRegistryKey, 0, -1);
        assertNotNull(registry);
        assertTrue(registry.contains(expectedLease1Key));

        // 2. Consume script with namespaced key
        LeaseConsumptionResult consumeResult = coordinator.tryConsume(lease1, "node-1", clock.instant());
        assertTrue(consumeResult.isConsumed());
        assertEquals(19, consumeResult.getRemainingCapacity());
        assertEquals("19", redisTemplate.opsForHash().get(expectedLease1Key, "remainingCapacity"));

        // 3. Renew script with namespaced key and registry key
        boolean renewed = coordinator.renewLease(lease1, "node-1", Duration.ofSeconds(45));
        assertTrue(renewed);
        Double score = redisTemplate.opsForZSet().score(expectedRegistryKey, expectedLease1Key);
        assertNotNull(score);
        assertEquals((double) lease1.getExpiresAt().toEpochMilli(), score);

        // 4. Expired lease reclamation inside acquire script
        // Advance clock past renewed expiry
        clock.advanceSeconds(80);
        // Node 2 acquires 95 units: Lua script reclaims lease 1's 19 remaining capacity (80 + 19 = 99), then allocates 95
        Optional<QuotaLease> lease2Opt = coordinator.acquireLease(quotaKey, "node-2", 95, Duration.ofSeconds(30));
        assertTrue(lease2Opt.isPresent());
        String expectedLease2Key = namespace + ":lease:" + lease2Opt.get().getLeaseId();

        // Verify lease 1 was deleted and removed from registry during reclamation
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(expectedLease1Key)));
        Set<String> registryAfterReclaim = redisTemplate.opsForZSet().range(expectedRegistryKey, 0, -1);
        assertNotNull(registryAfterReclaim);
        assertFalse(registryAfterReclaim.contains(expectedLease1Key));
        assertTrue(registryAfterReclaim.contains(expectedLease2Key));
        assertEquals("4", redisTemplate.opsForValue().get(expectedCapacityKey));

        // 5. Release script with namespaced keys
        boolean released = coordinator.releaseLease(lease2Opt.get());
        assertTrue(released);
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(expectedLease2Key)));
        Set<String> registryAfterRelease = redisTemplate.opsForZSet().range(expectedRegistryKey, 0, -1);
        assertNotNull(registryAfterRelease);
        assertFalse(registryAfterRelease.contains(expectedLease2Key));
        assertEquals("99", redisTemplate.opsForValue().get(expectedCapacityKey));
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
