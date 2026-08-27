package com.yashdotdev.distributed_traffic_control.policy;

import com.yashdotdev.distributed_traffic_control.traffic.TrafficRequest;

import java.util.Optional;

public class InMemoryPolicyProvider implements PolicyProvider{

    private final TrafficPolicy defaultPolicy;

    public InMemoryPolicyProvider(TrafficPolicy defaultPolicy){
        this.defaultPolicy = defaultPolicy;
    }

    @Override
    public Optional<TrafficPolicy> findPolicy(TrafficRequest request) {
        return Optional.of(defaultPolicy);
    }
}
