package com.yashdotdev.distributed_traffic_control.quota;

public interface  QuotaCoordinator {

    Quota acquireQuota(QuotaKey quotaKey, long capacity);
}
