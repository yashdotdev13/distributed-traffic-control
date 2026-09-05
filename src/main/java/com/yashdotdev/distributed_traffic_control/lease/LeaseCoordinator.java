package com.yashdotdev.distributed_traffic_control.lease;

import com.yashdotdev.distributed_traffic_control.quota.QuotaKey;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public interface LeaseCoordinator {

    void registerCapacity(
            GlobalCapacityKey capacityKey,
            long capacity
    );

    boolean removeCapacity(
            GlobalCapacityKey capacityKey
    );

    Optional<QuotaLease> acquireLease(
            QuotaKey quotaKey,
            String nodeId,
            long requestedCapacity,
            Duration leaseDuration
    );

    LeaseConsumptionResult tryConsume(
            QuotaLease lease,
            String nodeId,
            Instant currentTime
    );

    boolean renewLease(
            QuotaLease lease,
            String nodeId,
            Duration extension
    );

    boolean releaseLease(
            QuotaLease lease
    );
}