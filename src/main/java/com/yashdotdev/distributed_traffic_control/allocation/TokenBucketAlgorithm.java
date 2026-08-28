package com.yashdotdev.distributed_traffic_control.allocation;

import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicy;
import com.yashdotdev.distributed_traffic_control.quota.Quota;
import com.yashdotdev.distributed_traffic_control.quota.QuotaConsumptionResult;

public class TokenBucketAlgorithm implements TrafficControlAlgorithm{


    @Override
    public QuotaConsumptionResult tryConsume(Quota quota, TrafficPolicy policy) {


        if(!quota.hasAvailableCapacity()){
            return new QuotaConsumptionResult(false, quota.getAvailableCapacity());
        }

        quota.consume();

        return new QuotaConsumptionResult(true, quota.getAvailableCapacity());
    }
}
