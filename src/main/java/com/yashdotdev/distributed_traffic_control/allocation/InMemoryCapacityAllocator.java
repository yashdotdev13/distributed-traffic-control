package com.yashdotdev.distributed_traffic_control.allocation;

import com.yashdotdev.distributed_traffic_control.lease.LeaseCoordinator;
import com.yashdotdev.distributed_traffic_control.lease.QuotaLease;
import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicy;
import com.yashdotdev.distributed_traffic_control.quota.QuotaKey;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.util.Optional;

@RequiredArgsConstructor
public class InMemoryCapacityAllocator implements CapacityAllocator {

    private final LeaseCoordinator leaseCoordinator;
    private final AllocationStrategy allocationStrategy;
    private final AllocationProperties allocationProperties;

    @Override
    public Optional<QuotaLease> allocate(
            TrafficPolicy policy,
            QuotaKey quotaKey
    ) {
        long allocationCapacity =
                allocationStrategy.determineCapacity(policy);

        return leaseCoordinator.acquireLease(
                quotaKey,
                allocationProperties.getNodeId(),
                allocationCapacity,
                allocationProperties.getLeaseDuration()
        );
    }

}