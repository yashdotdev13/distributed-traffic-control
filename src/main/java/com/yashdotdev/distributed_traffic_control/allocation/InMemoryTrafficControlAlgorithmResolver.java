package com.yashdotdev.distributed_traffic_control.allocation;

import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicyType;

import java.util.Map;
import java.util.Objects;

public class InMemoryTrafficControlAlgorithmResolver implements TrafficControlAlgorithmResolver{

    private final Map<TrafficPolicyType, TrafficControlAlgorithm> algorithms;

    public InMemoryTrafficControlAlgorithmResolver(
            Map<TrafficPolicyType, TrafficControlAlgorithm> algorithms
    ) {
        this.algorithms = Map.copyOf(
                Objects.requireNonNull(
                        algorithms,
                        "algorithms must not be null"
                )
        );
    }
    @Override
    public TrafficControlAlgorithm resolve(TrafficPolicyType policyType) {
        TrafficControlAlgorithm algorithm = algorithms.get(policyType);

        if (algorithm == null) {
            throw new IllegalArgumentException(
                    "No traffic control algorithm registered for policy type: "
                            + policyType
            );
        }

        return algorithm;
    }
}
