package com.yashdotdev.distributed_traffic_control.quota;

import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicy;

public interface QuotaCoordinator {

    QuotaConsumptionResult tryConsume(
            QuotaKey quotaKey,
            TrafficPolicy policy
    );


}