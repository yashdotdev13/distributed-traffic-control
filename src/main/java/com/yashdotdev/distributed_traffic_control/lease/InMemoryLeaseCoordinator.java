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

        availableCapacityByQuota.putIfAbsent(
                buildGlobalKey(capacityKey),
                capacity
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

        return availableCapacityByQuota.remove(
                buildGlobalKey(capacityKey)
        ) != null;
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

        GlobalCapacityKey capacityKey =
                new GlobalCapacityKey(
                        quotaKey.getPolicyId(),
                        quotaKey.getResources()
                );

        String key =
                buildGlobalKey(capacityKey);

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

        synchronized (activeLeases) {

            QuotaLease activeLease =
                    activeLeases.get(lease.getLeaseId());

            if (activeLease == null) {
                return new LeaseConsumptionResult(
                        false,
                        0
                );
            }

            if (!activeLease.getNodeId().equals(nodeId)) {
                return new LeaseConsumptionResult(
                        false,
                        activeLease.getRemainingCapacity()
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
                QuotaKey quotaKey =
                        activeLease.getQuotaKey();

                String key =
                        buildGlobalKey(
                                new GlobalCapacityKey(
                                        quotaKey.getPolicyId(),
                                        quotaKey.getResources()
                                )
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

                        QuotaKey quotaKey =
                                lease.getQuotaKey();

                        String key =
                                buildGlobalKey(
                                        new GlobalCapacityKey(
                                                quotaKey.getPolicyId(),
                                                quotaKey.getResources()
                                        )
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

        synchronized (activeLeases) {

            QuotaLease activeLease =
                    activeLeases.get(lease.getLeaseId());

            if (activeLease == null) {
                return false;
            }

            if (!activeLease.getNodeId().equals(nodeId)) {
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

    private String buildGlobalKey(
            GlobalCapacityKey capacityKey
    ) {
        return String.join(
                ":",
                capacityKey.policyId(),
                capacityKey.resource()
        );
    }


}