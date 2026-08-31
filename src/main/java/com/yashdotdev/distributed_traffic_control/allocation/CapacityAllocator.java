package com.yashdotdev.distributed_traffic_control.allocation;

import com.yashdotdev.distributed_traffic_control.lease.QuotaLease;
import com.yashdotdev.distributed_traffic_control.quota.QuotaKey;

import java.time.Duration;
import java.util.Optional;

public interface CapacityAllocator {

    Optional<QuotaLease> allocate(
            QuotaKey quotaKey,
            String nodeId,
            long requestedCapacity,
            Duration leaseDuration
    );
}