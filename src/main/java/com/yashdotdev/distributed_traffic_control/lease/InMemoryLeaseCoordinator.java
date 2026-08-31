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

    private final Map<String, QuotaLease> activeLeases =
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

            reclaimExpiredLeases();

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

            activeLeases.put(
                    quotaLease.getLeaseId(),
                    quotaLease
            );

            return Optional.of(quotaLease);
        }
    }

    @Override
    public LeaseConsumptionResult tryConsume(
            QuotaLease lease,
            Instant currentTime
    ) {
        if (lease == null) {
            throw new IllegalArgumentException(
                    "lease must not be null"
            );
        }

        if (currentTime == null) {
            throw new IllegalArgumentException(
                    "currentTime must not be null"
            );
        }

        synchronized (activeLeases) {

            QuotaLease activeLease =
                    activeLeases.get(lease.getLeaseId());

            if (activeLease == null) {
                return new LeaseConsumptionResult(
                        false,
                        0
                );
            }

            if (activeLease.isExpired(currentTime)) {
                return new LeaseConsumptionResult(
                        false,
                        activeLease.getRemainingCapacity()
                );
            }

            if (!activeLease.hasRemainingCapacity()) {
                return new LeaseConsumptionResult(
                        false,
                        0
                );
            }

            activeLease.consume();

            return new LeaseConsumptionResult(
                    true,
                    activeLease.getRemainingCapacity()
            );
        }
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

        synchronized (activeLeases) {

            QuotaLease activeLease =
                    activeLeases.remove(
                            lease.getLeaseId()
                    );

            if (activeLease == null) {
                return false;
            }

            Instant currentTime = clock.instant();

            if (activeLease.isExpired(currentTime)) {
                return false;
            }

            long remainingCapacity =
                    activeLease.getRemainingCapacity();

            if (remainingCapacity > 0) {
                String key = buildKey(
                        activeLease.getQuotaKey()
                );

                availableCapacityByQuota.merge(
                        key,
                        remainingCapacity,
                        Long::sum
                );
            }

            return true;
        }
    }
    private void reclaimExpiredLeases() {

        Instant currentTime = clock.instant();

        activeLeases.values()
                .removeIf(lease -> {

                    if (!lease.isExpired(currentTime)) {
                        return false;
                    }

                    long unusedCapacity =
                            lease.getRemainingCapacity();

                    if (unusedCapacity > 0) {
                        String key = buildKey(
                                lease.getQuotaKey()
                        );

                        availableCapacityByQuota.merge(
                                key,
                                unusedCapacity,
                                Long::sum
                        );
                    }

                    return true;
                });
    }
    @Override
    public boolean renewLease(
            QuotaLease lease,
            Duration extension
    ) {
        if (lease == null) {
            throw new IllegalArgumentException(
                    "lease must not be null"
            );
        }

        if (extension == null
                || extension.isZero()
                || extension.isNegative()) {
            throw new IllegalArgumentException(
                    "extension must be greater than zero"
            );
        }

        synchronized (activeLeases) {

            QuotaLease activeLease =
                    activeLeases.get(lease.getLeaseId());

            if (activeLease == null) {
                return false;
            }

            Instant currentTime = clock.instant();

            if (activeLease.isExpired(currentTime)) {
                return false;
            }

            activeLease.renew(extension);

            return true;
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

        if (leaseDuration == null
                || leaseDuration.isZero()
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