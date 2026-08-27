package com.yashdotdev.distributed_traffic_control.quota;


public interface QuotaCoordinator {

    QuotaConsumptionResult tryConsume(
            QuotaKey quotaKey,
            long capacity
    );
}