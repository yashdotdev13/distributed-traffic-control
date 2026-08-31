package com.yashdotdev.distributed_traffic_control.allocation;

import com.yashdotdev.distributed_traffic_control.lease.LeaseCoordinator;
import com.yashdotdev.distributed_traffic_control.lease.QuotaLease;
import com.yashdotdev.distributed_traffic_control.quota.QuotaKey;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.util.Optional;

@RequiredArgsConstructor
public class InMemoryCapacityAllocator implements CapacityAllocator {

    private final LeaseCoordinator leaseCoordinator;

    @Override
    public Optional<QuotaLease> allocate(
            QuotaKey quotaKey,
            String nodeId,
            long requestedCapacity,
            Duration leaseDuration
    ) {
        return leaseCoordinator.acquireLease(
                quotaKey,
                nodeId,
                requestedCapacity,
                leaseDuration
        );
    }
}