package com.yashdotdev.distributed_traffic_control.policy;

import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Getter
public class TrafficPolicy {

    private final String policyId;

    private final String name;

    private final TrafficPolicyType type;

    private final PolicyStatus status;

    private final long capacity;

    private final long refillRate;

    private final Duration windowDuration;

    private final Instant createdAt;

    public TrafficPolicy(
            String policyId,
            String name,
            TrafficPolicyType type,
            PolicyStatus status,
            long capacity,
            long refillRate,
            Duration windowDuration,
            Instant createdAt
    ) {
        this.policyId = Objects.requireNonNull(
                policyId,
                "policyId must not be null"
        );

        this.name = Objects.requireNonNull(
                name,
                "name must not be null"
        );

        this.type = Objects.requireNonNull(
                type,
                "type must not be null"
        );

        this.status = Objects.requireNonNull(
                status,
                "status must not be null"
        );

        if (capacity <= 0) {
            throw new IllegalArgumentException(
                    "capacity must be greater than zero"
            );
        }

        if (refillRate <= 0) {
            throw new IllegalArgumentException(
                    "refillRate must be greater than zero"
            );
        }

        this.capacity = capacity;

        this.refillRate = refillRate;

        if (windowDuration != null
                && (windowDuration.isZero()
                || windowDuration.isNegative())) {
            throw new IllegalArgumentException(
                    "windowDuration must be greater than zero"
            );
        }

        this.windowDuration = windowDuration;

        this.createdAt = Objects.requireNonNull(
                createdAt,
                "createdAt must not be null"
        );
    }

    public boolean isActive() {
        return status == PolicyStatus.ACTIVE;
    }
}