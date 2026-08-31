package com.yashdotdev.distributed_traffic_control.lease;

import com.yashdotdev.distributed_traffic_control.quota.QuotaKey;

import java.util.Optional;

public interface LeaseStore {

    Optional<QuotaLease> find(QuotaKey quotaKey);

    void save(QuotaLease lease);

    void remove(QuotaKey quotaKey);
}
