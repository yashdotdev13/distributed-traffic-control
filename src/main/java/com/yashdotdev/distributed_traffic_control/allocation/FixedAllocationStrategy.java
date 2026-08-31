package com.yashdotdev.distributed_traffic_control.allocation;


import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicy;

public class FixedAllocationStrategy implements AllocationStrategy {

    @Override
    public long determineCapacity(TrafficPolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException(
                    "policy must not be null"
            );
        }

        if (policy.getCapacity() <= 0) {
            throw new IllegalArgumentException(
                    "policy capacity must be greater than zero"
            );
        }

        return policy.getCapacity();
    }
}