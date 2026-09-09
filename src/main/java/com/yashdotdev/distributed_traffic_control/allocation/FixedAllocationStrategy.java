package com.yashdotdev.distributed_traffic_control.allocation;

import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicy;

public class FixedAllocationStrategy implements AllocationStrategy {
    private final AllocationProperties allocationProperties;

    /**
     * Legacy/default constructor.
     * <p>
     * Preserves the original behavior where the allocation
     * capacity is the policy capacity.
     */
    public FixedAllocationStrategy() {
        this.allocationProperties = null;
    }

    /**
     * Configured constructor used by Spring.
     * <p>
     * Allows lease allocation to be bounded independently
     * from the total policy capacity.
     */
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

        // Preserve original behavior for existing callers/tests.
        if (allocationProperties == null) {
            return policy.getCapacity();
        }

        long leaseCapacity = allocationProperties.getLeaseCapacity();
        if (leaseCapacity <= 0) {
            throw new IllegalArgumentException("lease capacity must be greater than zero");
        }
        return Math.min(policy.getCapacity(), leaseCapacity);
    }
}