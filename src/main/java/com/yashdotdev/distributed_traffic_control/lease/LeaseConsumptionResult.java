package com.yashdotdev.distributed_traffic_control.lease;

import lombok.Getter;

@Getter
public class LeaseConsumptionResult {

    private final boolean consumed;

    private final long remainingCapacity;

    public LeaseConsumptionResult(
            boolean consumed,
            long remainingCapacity
    ) {
        this.consumed = consumed;
        this.remainingCapacity = remainingCapacity;
    }
}