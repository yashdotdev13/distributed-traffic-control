package com.yashdotdev.distributed_traffic_control.allocation;

import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicy;

public interface  AllocationStrategy {

    long determineCapacity(TrafficPolicy policy);
}
