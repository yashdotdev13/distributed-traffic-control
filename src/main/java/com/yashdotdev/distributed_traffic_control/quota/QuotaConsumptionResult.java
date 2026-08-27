package com.yashdotdev.distributed_traffic_control.quota;


import lombok.Getter;

@Getter
public class QuotaConsumptionResult {

    private final boolean consumed;
    private final long remainingCapacity;

    public QuotaConsumptionResult(boolean consumed,
                                  long remainingCapacity){
        this.consumed = consumed;
        this.remainingCapacity = remainingCapacity;
    }
}
