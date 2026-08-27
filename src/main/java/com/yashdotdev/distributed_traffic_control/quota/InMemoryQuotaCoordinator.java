package com.yashdotdev.distributed_traffic_control.quota;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryQuotaCoordinator implements QuotaCoordinator{

    private final Map<String, Quota> quotas = new ConcurrentHashMap<>();

    @Override
    public Quota acquireQuota(QuotaKey quotaKey, long capacity) {

        String key = buildKey(quotaKey);

        return quotas.computeIfAbsent(key,
                ignored->new Quota(
                        quotaKey,
                        capacity,
                        capacity
                ));
    }

    private String buildKey(QuotaKey quotaKey) {
        return String.join(
                ":",
                quotaKey.getPolicyId(),
                quotaKey.getSubject().getType().name(),
                quotaKey.getSubject().getSubjectId(),
                quotaKey.getResources()
        );
    }
}
