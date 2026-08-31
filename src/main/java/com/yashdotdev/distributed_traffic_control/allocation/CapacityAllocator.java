package com.yashdotdev.distributed_traffic_control.allocation;

import com.yashdotdev.distributed_traffic_control.lease.QuotaLease;
import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicy;
import com.yashdotdev.distributed_traffic_control.quota.QuotaKey;

import java.util.Optional;

public interface CapacityAllocator {

    Optional<QuotaLease> allocate(
            TrafficPolicy policy,
            QuotaKey quotaKey
    );
}