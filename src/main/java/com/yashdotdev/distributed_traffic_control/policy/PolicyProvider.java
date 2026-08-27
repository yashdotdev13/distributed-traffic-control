package com.yashdotdev.distributed_traffic_control.policy;

import com.yashdotdev.distributed_traffic_control.traffic.TrafficRequest;

import java.util.Optional;

public interface PolicyProvider {

    Optional<TrafficPolicy> findPolicy(TrafficRequest request);
}
