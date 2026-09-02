package com.yashdotdev.distributed_traffic_control.traffic.algorithm;

import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicy;
import com.yashdotdev.distributed_traffic_control.quota.Quota;
import com.yashdotdev.distributed_traffic_control.quota.QuotaConsumptionResult;

public interface TrafficControlAlgorithm {

    QuotaConsumptionResult tryConsume(
            Quota quota,
            TrafficPolicy policy
    );
}