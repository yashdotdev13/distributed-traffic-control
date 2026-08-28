package com.yashdotdev.distributed_traffic_control.allocation;

import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicyType;

public interface  TrafficControlAlgorithmResolver {


    TrafficControlAlgorithm resolve(TrafficPolicyType policyType);
}
