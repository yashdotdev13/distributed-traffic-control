package com.yashdotdev.distributed_traffic_control.quota;

import lombok.Getter;
import java.util.Objects;

@Getter
public class Quota {

    private final QuotaKey quotaKey;
    private final long capacity;
    private long availableCapacity;

    public Quota(
            QuotaKey quotaKey,
            long capacity,
            long availableCapacity
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

        if (availableCapacity < 0 || availableCapacity > capacity) {
            throw new IllegalArgumentException(
                    "availableCapacity must be between zero and capacity"
            );
        }

        this.capacity = capacity;
        this.availableCapacity = availableCapacity;
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
}