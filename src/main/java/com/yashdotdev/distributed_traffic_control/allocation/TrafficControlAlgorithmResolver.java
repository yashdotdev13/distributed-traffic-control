package com.yashdotdev.distributed_traffic_control.allocation;

import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicyType;
import com.yashdotdev.distributed_traffic_control.traffic.algorithm.TrafficControlAlgorithm;

public interface TrafficControlAlgorithmResolver {

    TrafficControlAlgorithm resolve(
            TrafficPolicyType policyType
    );
}