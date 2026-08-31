package com.yashdotdev.distributed_traffic_control.lease;

import com.yashdotdev.distributed_traffic_control.quota.QuotaKey;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryLeaseStore implements LeaseStore {

    private final Map<QuotaKey, QuotaLease> leases =
            new ConcurrentHashMap<>();

    @Override
    public Optional<QuotaLease> find(QuotaKey quotaKey) {
        return Optional.ofNullable(
                leases.get(quotaKey)
        );
    }

    @Override
    public void save(QuotaLease lease) {
        leases.put(
                lease.getQuotaKey(),
                lease
        );
    }

    @Override
    public void remove(QuotaKey quotaKey) {
        leases.remove(quotaKey);
    }
}