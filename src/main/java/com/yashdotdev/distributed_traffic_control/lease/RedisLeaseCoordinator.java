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

    /**
     * Atomically:
     *
     * 1. Reads the available global capacity.
     * 2. Verifies that enough capacity exists.
     * 3. Decrements the global capacity.
     * 4. Creates the lease.
     * 5. Applies the lease TTL.
     *
     * Returns:
     * 1 -> lease created
     * 0 -> insufficient/unregistered capacity
     */
    private static final DefaultRedisScript<Long> ACQUIRE_LEASE_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    local available = redis.call('GET', KEYS[1])

                    if not available then
                        return 0
                    end

                    available = tonumber(available)

                    local requested = tonumber(ARGV[1])

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
                        'nodeId', ARGV[2],
                        'allocatedCapacity', ARGV[3],
                        'remainingCapacity', ARGV[4],
                        'issuedAt', ARGV[5],
                        'expiresAt', ARGV[6]
                    )

                    redis.call(
                        'PEXPIRE',
                        KEYS[2],
                        ARGV[7]
                    )

                    return 1
                    """,
                    Long.class
            );

    /**
     * Atomically:
     *
     * 1. Verifies that the lease exists.
     * 2. Verifies that the lease belongs to the requested node.
     * 3. Verifies that the lease has not expired.
     * 4. Verifies that remaining capacity is available.
     * 5. Decrements remaining capacity.
     *
     * Returns:
     *  1 -> consumed
     *  0 -> lease cannot be consumed
     *
     * The remaining capacity is returned as the second
     * result value through the Redis hash itself.
     */
    private static final DefaultRedisScript<Long> CONSUME_LEASE_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    if redis.call('EXISTS', KEYS[1]) == 0 then
                        return -1
                    end
    
                    local leaseNodeId =
                        redis.call('HGET', KEYS[1], 'nodeId')
    
                    if leaseNodeId ~= ARGV[1] then
                        return -2
                    end
    
                    local expiresAt =
                        redis.call('HGET', KEYS[1], 'expiresAt')
    
                    if not expiresAt then
                        return -1
                    end
    
                    local currentTimeMillis =
                        tonumber(ARGV[2])
    
                    local expiresAtMillis =
                        tonumber(expiresAt)
    
                    if currentTimeMillis >= expiresAtMillis then
                        return -3
                    end
    
                    local remaining =
                        tonumber(
                            redis.call(
                                'HGET',
                                KEYS[1],
                                'remainingCapacity'
                            )
                        )
    
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
     * Atomically:
     *
     * 1. Verifies that the lease exists.
     * 2. Verifies that the lease belongs to the requested node.
     * 3. Verifies that the lease has not expired.
     * 4. Extends the existing expiration timestamp.
     * 5. Updates the Redis key TTL.
     *
     * Returns:
     *  1 -> renewed
     *  0 -> renewal rejected
     */
    private static final DefaultRedisScript<Long> RENEW_LEASE_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    if redis.call('EXISTS', KEYS[1]) == 0 then
                        return 0
                    end
    
                    local leaseNodeId =
                        redis.call('HGET', KEYS[1], 'nodeId')
    
                    if leaseNodeId ~= ARGV[1] then
                        return 0
                    end
    
                    local expiresAt =
                        tonumber(
                            redis.call(
                                'HGET',
                                KEYS[1],
                                'expiresAt'
                            )
                        )
    
                    if not expiresAt then
                        return 0
                    end
    
                    local currentTimeMillis =
                        tonumber(ARGV[2])
    
                    if currentTimeMillis >= expiresAt then
                        return 0
                    end
    
                    local extensionMillis =
                        tonumber(ARGV[3])
    
                    local newExpiresAt =
                        expiresAt + extensionMillis
    
                    redis.call(
                        'HSET',
                        KEYS[1],
                        'expiresAt',
                        newExpiresAt
                    )
    
                    redis.call(
                        'PEXPIRE',
                        KEYS[1],
                        newExpiresAt - currentTimeMillis
                    )
    
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
    public void registerQuota(
            QuotaKey quotaKey,
            long capacity
    ) {
        validateQuotaKey(quotaKey);

        if (capacity <= 0) {
            throw new IllegalArgumentException(
                    "capacity must be greater than zero"
            );
        }

        redisTemplate.opsForValue().setIfAbsent(
                buildQuotaRedisKey(quotaKey),
                String.valueOf(capacity)
        );
    }

    @Override
    public boolean removeQuota(
            QuotaKey quotaKey
    ) {
        validateQuotaKey(quotaKey);

        Boolean deleted =
                redisTemplate.delete(
                        buildQuotaRedisKey(quotaKey)
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

        Instant issuedAt =
                clock.instant();

        Instant expiresAt =
                issuedAt.plus(leaseDuration);

        String leaseId =
                UUID.randomUUID().toString();

        String quotaRedisKey =
                buildQuotaRedisKey(quotaKey);

        String leaseRedisKey =
                buildLeaseRedisKey(leaseId);

        Long result =
                redisTemplate.execute(
                        ACQUIRE_LEASE_SCRIPT,
                        List.of(
                                quotaRedisKey,
                                leaseRedisKey
                        ),
                        String.valueOf(requestedCapacity),
                        nodeId,
                        String.valueOf(requestedCapacity),
                        String.valueOf(requestedCapacity),
                        issuedAt.toString(),
                        String.valueOf(
                                expiresAt.toEpochMilli()
                        ),
                        String.valueOf(
                                leaseDuration.toMillis()
                        )
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

        Long result =
                redisTemplate.execute(
                        CONSUME_LEASE_SCRIPT,
                        List.of(leaseRedisKey),
                        nodeId,
                        String.valueOf(
                                currentTime.toEpochMilli()
                        )
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

        /*
         * Keep the domain lease object synchronized with Redis.
         */
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

        Instant currentTime =
                clock.instant();

        Long result =
                redisTemplate.execute(
                        RENEW_LEASE_SCRIPT,
                        List.of(leaseRedisKey),
                        nodeId,
                        String.valueOf(
                                currentTime.toEpochMilli()
                        ),
                        String.valueOf(
                                extension.toMillis()
                        )
                );

        if (!Long.valueOf(1L).equals(result)) {
            return false;
        }

        /*
         * Keep the local domain object synchronized
         * with the successful Redis renewal.
         */
        lease.renew(extension);

        return true;
    }

    @Override
    public boolean releaseLease(
            QuotaLease lease
    ) {
        throw new UnsupportedOperationException(
                "Redis lease release will be implemented next"
        );
    }

    private String buildQuotaRedisKey(
            QuotaKey quotaKey
    ) {
        return QUOTA_KEY_PREFIX
                + buildQuotaIdentity(quotaKey);
    }

    private String buildQuotaIdentity(
            QuotaKey quotaKey
    ) {
        return String.join(
                ":",
                quotaKey.getPolicyId(),
                quotaKey.getSubject()
                        .getType()
                        .name(),
                quotaKey.getSubject()
                        .getSubjectId(),
                quotaKey.getResources()
        );
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