package com.yashdotdev.distributed_traffic_control.policy;

import java.util.Objects;

public record PolicyRegistration(
        String resource,
        TrafficPolicy policy
) {

    public PolicyRegistration {

        if (resource == null || resource.isBlank()) {
            throw new IllegalArgumentException(
                    "resource must not be null or blank"
            );
        }

        Objects.requireNonNull(
                policy,
                "policy must not be null"
        );
    }
}