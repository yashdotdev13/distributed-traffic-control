package com.yashdotdev.distributed_traffic_control.lease;

import com.yashdotdev.distributed_traffic_control.quota.QuotaKey;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class RedisLeaseCoordinator implements LeaseCoordinator {

    private static final String QUOTA_KEY_PREFIX =
            "traffic-control:quota:";

    private static final String LEASE_KEY_PREFIX =
            "traffic-control:lease:";

    private static final String LEASE_REGISTRY_SUFFIX =
            ":leases";

    /**
     * Atomically:
     *
     * 1. Finds expired leases for the global capacity key.
     * 2. Returns their unused capacity to the global pool.
     * 3. Deletes the expired lease hashes.
     * 4. Removes expired leases from the registry.
     * 5. Checks whether enough global capacity is available.
     * 6. Decrements global capacity.
     * 7. Creates the new lease hash.
     * 8. Adds the new lease to the expiry registry.
     *
     * KEYS[1] = global capacity key
     * KEYS[2] = new lease key
     * KEYS[3] = lease registry key
     *
     * ARGV[1] = requested capacity
     * ARGV[2] = node id
     * ARGV[3] = issued at ISO-8601 string
     * ARGV[4] = expires at epoch millis
     * ARGV[5] = current time epoch millis
     *
     * Returns:
     * 1 = lease acquired
     * 0 = insufficient/unregistered capacity
     */
    private static final DefaultRedisScript<Long> ACQUIRE_LEASE_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    local available = redis.call('GET', KEYS[1])

                    if not available then
                        return 0
                    end

                    available = tonumber(available)

                    local currentTimeMillis = tonumber(ARGV[5])

                    local expiredLeaseKeys = redis.call(
                        'ZRANGEBYSCORE',
                        KEYS[3],
                        '-inf',
                        currentTimeMillis
                    )

                    for _, expiredLeaseKey in ipairs(expiredLeaseKeys) do
                        if redis.call('EXISTS', expiredLeaseKey) == 1 then
                            local remainingValue = redis.call(
                                'HGET',
                                expiredLeaseKey,
                                'remainingCapacity'
                            )

                            if remainingValue then
                                local remaining = tonumber(remainingValue)

                                if remaining and remaining > 0 then
                                    redis.call(
                                        'INCRBY',
                                        KEYS[1],
                                        remaining
                                    )

                                    available = available + remaining
                                end
                            end

                            redis.call(
                                'DEL',
                                expiredLeaseKey
                            )
                        end

                        redis.call(
                            'ZREM',
                            KEYS[3],
                            expiredLeaseKey
                        )
                    end

                    local requested = tonumber(ARGV[1])

                    if not requested or requested <= 0 then
                        return 0
                    end

                    if available < requested then
                        return 0
                    end

                    redis.call(
                        'DECRBY',
                        KEYS[1],
                        requested
                    )

                    redis.call(
                        'HSET',
                        KEYS[2],
                        'nodeId',
                        ARGV[2],
                        'allocatedCapacity',
                        ARGV[1],
                        'remainingCapacity',
                        ARGV[1],
                        'issuedAt',
                        ARGV[3],
                        'expiresAt',
                        ARGV[4]
                    )

                    redis.call(
                        'ZADD',
                        KEYS[3],
                        ARGV[4],
                        KEYS[2]
                    )

                    return 1
                    """,
                    Long.class
            );

    /**
     * Atomically consumes one unit from a lease.
     *
     * Returns:
     * >= 0 -> remaining capacity after consumption
     * -1   -> lease missing
     * -2   -> wrong owner
     * -3   -> lease expired
     * -4   -> lease exhausted
     */
    private static final DefaultRedisScript<Long> CONSUME_LEASE_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    if redis.call('EXISTS', KEYS[1]) == 0 then
                        return -1
                    end

                    local leaseNodeId = redis.call(
                        'HGET',
                        KEYS[1],
                        'nodeId'
                    )

                    if leaseNodeId ~= ARGV[1] then
                        return -2
                    end

                    local expiresAtValue = redis.call(
                        'HGET',
                        KEYS[1],
                        'expiresAt'
                    )

                    if not expiresAtValue then
                        return -1
                    end

                    local expiresAt = tonumber(expiresAtValue)
                    local currentTimeMillis = tonumber(ARGV[2])

                    if not expiresAt then
                        return -1
                    end

                    if currentTimeMillis >= expiresAt then
                        return -3
                    end

                    local remainingValue = redis.call(
                        'HGET',
                        KEYS[1],
                        'remainingCapacity'
                    )

                    if not remainingValue then
                        return -4
                    end

                    local remaining = tonumber(remainingValue)

                    if not remaining or remaining <= 0 then
                        return -4
                    end

                    remaining = remaining - 1

                    redis.call(
                        'HSET',
                        KEYS[1],
                        'remainingCapacity',
                        remaining
                    )

                    return remaining
                    """,
                    Long.class
            );

    /**
     * Atomically renews a lease.
     *
     * KEYS[1] = lease key
     * KEYS[2] = lease registry key
     *
     * ARGV[1] = node id
     * ARGV[2] = current time epoch millis
     * ARGV[3] = extension millis
     *
     * Returns:
     * 1 = renewed
     * 0 = renewal rejected
     */
    private static final DefaultRedisScript<Long> RENEW_LEASE_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    if redis.call('EXISTS', KEYS[1]) == 0 then
                        return 0
                    end

                    local leaseNodeId = redis.call(
                        'HGET',
                        KEYS[1],
                        'nodeId'
                    )

                    if leaseNodeId ~= ARGV[1] then
                        return 0
                    end

                    local expiresAtValue = redis.call(
                        'HGET',
                        KEYS[1],
                        'expiresAt'
                    )

                    if not expiresAtValue then
                        return 0
                    end

                    local expiresAt = tonumber(expiresAtValue)
                    local currentTimeMillis = tonumber(ARGV[2])
                    local extensionMillis = tonumber(ARGV[3])

                    if not expiresAt then
                        return 0
                    end

                    if not extensionMillis or extensionMillis <= 0 then
                        return 0
                    end

                    if currentTimeMillis >= expiresAt then
                        return 0
                    end

                    local newExpiresAt =
                        expiresAt + extensionMillis

                    redis.call(
                        'HSET',
                        KEYS[1],
                        'expiresAt',
                        newExpiresAt
                    )

                    redis.call(
                        'ZADD',
                        KEYS[2],
                        newExpiresAt,
                        KEYS[1]
                    )

                    return 1
                    """,
                    Long.class
            );

    /**
     * Atomically:
     *
     * 1. Verifies lease exists.
     * 2. Reads remaining capacity.
     * 3. Deletes lease.
     * 4. Removes it from expiry registry.
     * 5. Returns unused capacity to global capacity.
     *
     * KEYS[1] = lease key
     * KEYS[2] = global capacity key
     * KEYS[3] = lease registry key
     */
    private static final DefaultRedisScript<Long> RELEASE_LEASE_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    if redis.call('EXISTS', KEYS[1]) == 0 then
                        return 0
                    end

                    local remainingValue = redis.call(
                        'HGET',
                        KEYS[1],
                        'remainingCapacity'
                    )

                    local remaining = 0

                    if remainingValue then
                        remaining = tonumber(remainingValue)

                        if not remaining then
                            remaining = 0
                        end
                    end

                    redis.call(
                        'DEL',
                        KEYS[1]
                    )

                    redis.call(
                        'ZREM',
                        KEYS[3],
                        KEYS[1]
                    )

                    if remaining > 0
                       and redis.call('EXISTS', KEYS[2]) == 1 then

                        redis.call(
                            'INCRBY',
                            KEYS[2],
                            remaining
                        )
                    end

                    return 1
                    """,
                    Long.class
            );

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;

    public RedisLeaseCoordinator(
            StringRedisTemplate redisTemplate
    ) {
        this(
                redisTemplate,
                Clock.systemUTC()
        );
    }

    public RedisLeaseCoordinator(
            StringRedisTemplate redisTemplate,
            Clock clock
    ) {
        if (redisTemplate == null) {
            throw new IllegalArgumentException(
                    "redisTemplate must not be null"
            );
        }

        if (clock == null) {
            throw new IllegalArgumentException(
                    "clock must not be null"
            );
        }

        this.redisTemplate = redisTemplate;
        this.clock = clock;
    }

    @Override
    public void registerCapacity(
            GlobalCapacityKey capacityKey,
            long capacity
    ) {
        if (capacityKey == null) {
            throw new IllegalArgumentException(
                    "capacityKey must not be null"
            );
        }

        if (capacity <= 0) {
            throw new IllegalArgumentException(
                    "capacity must be greater than zero"
            );
        }

        redisTemplate.opsForValue().setIfAbsent(
                buildGlobalCapacityRedisKey(capacityKey),
                String.valueOf(capacity)
        );
    }

    @Override
    public boolean removeCapacity(
            GlobalCapacityKey capacityKey
    ) {
        if (capacityKey == null) {
            throw new IllegalArgumentException(
                    "capacityKey must not be null"
            );
        }

        Boolean deleted = redisTemplate.delete(
                buildGlobalCapacityRedisKey(capacityKey)
        );

        return Boolean.TRUE.equals(deleted);
    }

    @Override
    public Optional<QuotaLease> acquireLease(
            QuotaKey quotaKey,
            String nodeId,
            long requestedCapacity,
            Duration leaseDuration
    ) {
        validateQuotaKey(quotaKey);

        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException(
                    "nodeId must not be null or blank"
            );
        }

        if (requestedCapacity <= 0) {
            throw new IllegalArgumentException(
                    "requestedCapacity must be greater than zero"
            );
        }

        if (leaseDuration == null
                || leaseDuration.isZero()
                || leaseDuration.isNegative()) {
            throw new IllegalArgumentException(
                    "leaseDuration must be greater than zero"
            );
        }

        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(leaseDuration);

        String leaseId = UUID.randomUUID().toString();

        GlobalCapacityKey capacityKey =
                new GlobalCapacityKey(
                        quotaKey.getPolicyId(),
                        quotaKey.getResources()
                );

        String quotaRedisKey =
                buildGlobalCapacityRedisKey(capacityKey);

        String leaseRedisKey =
                buildLeaseRedisKey(leaseId);

        String leaseRegistryRedisKey =
                buildLeaseRegistryRedisKey(capacityKey);

        Long result = redisTemplate.execute(
                ACQUIRE_LEASE_SCRIPT,
                List.of(
                        quotaRedisKey,
                        leaseRedisKey,
                        leaseRegistryRedisKey
                ),
                String.valueOf(requestedCapacity),
                nodeId,
                issuedAt.toString(),
                String.valueOf(expiresAt.toEpochMilli()),
                String.valueOf(issuedAt.toEpochMilli())
        );

        if (!Long.valueOf(1L).equals(result)) {
            return Optional.empty();
        }

        QuotaLease lease =
                new QuotaLease(
                        leaseId,
                        quotaKey,
                        nodeId,
                        requestedCapacity,
                        issuedAt,
                        expiresAt
                );

        return Optional.of(lease);
    }

    @Override
    public LeaseConsumptionResult tryConsume(
            QuotaLease lease,
            String nodeId,
            Instant currentTime
    ) {
        if (lease == null) {
            throw new IllegalArgumentException(
                    "lease must not be null"
            );
        }

        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException(
                    "nodeId must not be null or blank"
            );
        }

        if (currentTime == null) {
            throw new IllegalArgumentException(
                    "currentTime must not be null"
            );
        }

        String leaseRedisKey =
                buildLeaseRedisKey(
                        lease.getLeaseId()
                );

        Long result = redisTemplate.execute(
                CONSUME_LEASE_SCRIPT,
                List.of(leaseRedisKey),
                nodeId,
                String.valueOf(currentTime.toEpochMilli())
        );

        if (result == null) {
            return new LeaseConsumptionResult(
                    false,
                    0
            );
        }

        if (result == -1L) {
            return new LeaseConsumptionResult(
                    false,
                    0
            );
        }

        if (result == -2L) {
            return new LeaseConsumptionResult(
                    false,
                    lease.getRemainingCapacity()
            );
        }

        if (result == -3L) {
            return new LeaseConsumptionResult(
                    false,
                    lease.getRemainingCapacity()
            );
        }

        if (result == -4L) {
            return new LeaseConsumptionResult(
                    false,
                    0
            );
        }

        long remainingCapacity = result;

        if (lease.getRemainingCapacity() > 0) {
            lease.consume();
        }

        return new LeaseConsumptionResult(
                true,
                remainingCapacity
        );
    }

    @Override
    public boolean renewLease(
            QuotaLease lease,
            String nodeId,
            Duration extension
    ) {
        if (lease == null) {
            throw new IllegalArgumentException(
                    "lease must not be null"
            );
        }

        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException(
                    "nodeId must not be null or blank"
            );
        }

        if (extension == null
                || extension.isZero()
                || extension.isNegative()) {
            throw new IllegalArgumentException(
                    "extension must be greater than zero"
            );
        }

        String leaseRedisKey =
                buildLeaseRedisKey(
                        lease.getLeaseId()
                );

        GlobalCapacityKey capacityKey =
                new GlobalCapacityKey(
                        lease.getQuotaKey().getPolicyId(),
                        lease.getQuotaKey().getResources()
                );

        String leaseRegistryRedisKey =
                buildLeaseRegistryRedisKey(capacityKey);

        Instant currentTime = clock.instant();

        Long result = redisTemplate.execute(
                RENEW_LEASE_SCRIPT,
                List.of(
                        leaseRedisKey,
                        leaseRegistryRedisKey
                ),
                nodeId,
                String.valueOf(currentTime.toEpochMilli()),
                String.valueOf(extension.toMillis())
        );

        if (!Long.valueOf(1L).equals(result)) {
            return false;
        }

        lease.renew(extension);

        return true;
    }

    @Override
    public boolean releaseLease(
            QuotaLease lease
    ) {
        if (lease == null) {
            throw new IllegalArgumentException(
                    "lease must not be null"
            );
        }

        String leaseRedisKey =
                buildLeaseRedisKey(
                        lease.getLeaseId()
                );

        QuotaKey quotaKey =
                lease.getQuotaKey();

        GlobalCapacityKey capacityKey =
                new GlobalCapacityKey(
                        quotaKey.getPolicyId(),
                        quotaKey.getResources()
                );

        String quotaRedisKey =
                buildGlobalCapacityRedisKey(capacityKey);

        String leaseRegistryRedisKey =
                buildLeaseRegistryRedisKey(capacityKey);

        Long result = redisTemplate.execute(
                RELEASE_LEASE_SCRIPT,
                List.of(
                        leaseRedisKey,
                        quotaRedisKey,
                        leaseRegistryRedisKey
                )
        );

        return Long.valueOf(1L).equals(result);
    }

    private String buildGlobalCapacityRedisKey(
            GlobalCapacityKey capacityKey
    ) {
        return QUOTA_KEY_PREFIX
                + capacityKey.policyId()
                + ":"
                + capacityKey.resource();
    }

    private String buildLeaseRegistryRedisKey(
            GlobalCapacityKey capacityKey
    ) {
        return buildGlobalCapacityRedisKey(capacityKey)
                + LEASE_REGISTRY_SUFFIX;
    }

    private String buildLeaseRedisKey(
            String leaseId
    ) {
        return LEASE_KEY_PREFIX + leaseId;
    }

    private void validateQuotaKey(
            QuotaKey quotaKey
    ) {
        if (quotaKey == null) {
            throw new IllegalArgumentException(
                    "quotaKey must not be null"
            );
        }
    }
}