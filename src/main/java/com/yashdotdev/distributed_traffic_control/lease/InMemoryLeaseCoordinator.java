package com.yashdotdev.distributed_traffic_control.lease;

import com.yashdotdev.distributed_traffic_control.quota.QuotaKey;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryLeaseCoordinator implements LeaseCoordinator {

    private final Map<String, Long> availableCapacityByQuota =
            new ConcurrentHashMap<>();

    private final Clock clock;

    public InMemoryLeaseCoordinator() {
        this(Clock.systemUTC());
    }

    public InMemoryLeaseCoordinator(Clock clock) {
        this.clock = clock;
    }

    public void registerQuota(
            QuotaKey quotaKey,
            long capacity
    ) {
        if (capacity <= 0) {
            throw new IllegalArgumentException(
                    "capacity must be greater than zero"
            );
        }

        availableCapacityByQuota.putIfAbsent(
                buildKey(quotaKey),
                capacity
        );
    }

    @Override
    public Optional<QuotaLease> acquireLease(
            QuotaKey quotaKey,
            String nodeId,
            long requestedCapacity,
            Duration leaseDuration
    ) {
        validateRequest(
                quotaKey,
                nodeId,
                requestedCapacity,
                leaseDuration
        );

        String key = buildKey(quotaKey);

        synchronized (availableCapacityByQuota) {
            Long availableCapacity =
                    availableCapacityByQuota.get(key);

            if (availableCapacity == null
                    || availableCapacity < requestedCapacity) {
                return Optional.empty();
            }

            availableCapacityByQuota.put(
                    key,
                    availableCapacity - requestedCapacity
            );

            Instant issuedAt = clock.instant();

            QuotaLease quotaLease = new QuotaLease(
                    UUID.randomUUID().toString(),
                    quotaKey,
                    nodeId,
                    requestedCapacity,
                    issuedAt,
                    issuedAt.plus(leaseDuration)
            );

            return Optional.of(quotaLease);
        }
    }

    private void validateRequest(
            QuotaKey quotaKey,
            String nodeId,
            long requestedCapacity,
            Duration leaseDuration
    ) {
        if (quotaKey == null) {
            throw new IllegalArgumentException(
                    "quotaKey must not be null"
            );
        }

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

        if (leaseDuration == null || leaseDuration.isZero()
                || leaseDuration.isNegative()) {
            throw new IllegalArgumentException(
                    "leaseDuration must be greater than zero"
            );
        }
    }

    private String buildKey(QuotaKey quotaKey) {
        return String.join(
                ":",
                quotaKey.getPolicyId(),
                quotaKey.getSubject().getType().name(),
                quotaKey.getSubject().getSubjectId(),
                quotaKey.getResources()
        );
    }
}