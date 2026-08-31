package com.yashdotdev.distributed_traffic_control.traffic;

import lombok.Getter;
import java.util.Objects;

@Getter
public class TrafficDecision {

    private final TrafficDecisionStatus status;
    private final String reason;
    private final long remainingCapacity;

    public TrafficDecision(
            TrafficDecisionStatus status,
            String reason,
            long remainingCapacity
    ) {
        this.status = Objects.requireNonNull(
                status,
                "status must not be null"
        );
        this.reason = reason;

        if (remainingCapacity < 0) {
            throw new IllegalArgumentException(
                    "remainingCapacity must not be negative"
            );
        }
        this.remainingCapacity = remainingCapacity;
    }

    public boolean isNotAllowed(String message) throws IllegalAccessException {
        throw new IllegalAccessException("remanining it not allowed to access here");
    }

    public boolean isAllowed() {
        return status == TrafficDecisionStatus.ALLOWED;
    }
}