package com.yashdotdev.distributed_traffic_control.quota;

import lombok.Getter;

import java.time.Instant;
import java.util.Objects;

@Getter
public class Quota {

    private final QuotaKey quotaKey;
    private final long capacity;
    private long availableCapacity;
    private Instant lastRefilledAt;
    private Instant windowStartedAt;

    public Quota(
            QuotaKey quotaKey,
            long capacity,
            long availableCapacity,
            Instant lastRefilledAt
    ) {
        this(
                quotaKey,
                capacity,
                availableCapacity,
                lastRefilledAt,
                lastRefilledAt
        );
    }

    public Quota(
            QuotaKey quotaKey,
            long capacity,
            long availableCapacity,
            Instant lastRefilledAt,
            Instant windowStartedAt
    ) {
        this.quotaKey = Objects.requireNonNull(
                quotaKey,
                "quotaKey must not be null"
        );

        if (capacity <= 0) {
            throw new IllegalArgumentException(
                    "capacity must be greater than zero"
            );
        }

        if (availableCapacity < 0
                || availableCapacity > capacity) {
            throw new IllegalArgumentException(
                    "availableCapacity must be between zero and capacity"
            );
        }

        this.capacity = capacity;

        this.availableCapacity = availableCapacity;

        this.lastRefilledAt = Objects.requireNonNull(
                lastRefilledAt,
                "lastRefilledAt must not be null"
        );

        this.windowStartedAt = Objects.requireNonNull(
                windowStartedAt,
                "windowStartedAt must not be null"
        );
    }

    public boolean hasAvailableCapacity() {
        return availableCapacity > 0;
    }

    public void consume() {

        if (!hasAvailableCapacity()) {
            throw new IllegalStateException(
                    "quota has no available capacity"
            );
        }

        availableCapacity--;
    }

    public void refill(
            long tokens,
            Instant refilledAt
    ) {

        if (tokens <= 0) {
            return;
        }

        availableCapacity = Math.min(
                capacity,
                availableCapacity + tokens
        );

        lastRefilledAt = Objects.requireNonNull(
                refilledAt,
                "refilledAt must not be null"
        );
    }

    public void resetWindow(
            Instant windowStartedAt
    ) {

        this.availableCapacity = capacity;

        this.windowStartedAt = Objects.requireNonNull(
                windowStartedAt,
                "windowStartedAt must not be null"
        );
    }
}