package com.yashdotdev.distributed_traffic_control.allocation;

import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicy;

public class FixedAllocationStrategy implements AllocationStrategy {

    private final AllocationProperties allocationProperties;

    public FixedAllocationStrategy(AllocationProperties allocationProperties) {
        if (allocationProperties == null) {
            throw new IllegalArgumentException("allocationProperties must not be null");
        }

        this.allocationProperties = allocationProperties;
    }

    @Override
    public long determineCapacity(TrafficPolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("policy must not be null");
        }

        if (policy.getCapacity() <= 0) {
            throw new IllegalArgumentException("policy capacity must be greater than zero");
        }

        long leaseCapacity = allocationProperties.getLeaseCapacity();

        if (leaseCapacity <= 0) {
            throw new IllegalArgumentException("lease capacity must be greater than zero");
        }

        return Math.min(policy.getCapacity(), leaseCapacity);
    }
}