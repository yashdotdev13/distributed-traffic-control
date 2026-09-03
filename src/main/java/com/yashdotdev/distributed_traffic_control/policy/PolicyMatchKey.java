package com.yashdotdev.distributed_traffic_control.policy;

import com.yashdotdev.distributed_traffic_control.traffic.TrafficSubject;

import java.util.Objects;

public record PolicyMatchKey(
        TrafficSubject subject,
        String resource
) {

    public PolicyMatchKey {
        Objects.requireNonNull(
                subject,
                "subject must not be null"
        );

        if (resource == null || resource.isBlank()) {
            throw new IllegalArgumentException(
                    "resource must not be null or blank"
            );
        }
    }
}