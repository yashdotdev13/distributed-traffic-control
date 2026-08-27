package com.yashdotdev.distributed_traffic_control.quota;


import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryQuotaCoordinator implements QuotaCoordinator {

    private final Map<String, Quota> quotas = new ConcurrentHashMap<>();

    @Override
    public QuotaConsumptionResult tryConsume(
            QuotaKey quotaKey,
            long capacity
    ) {
        String key = buildKey(quotaKey);

        Quota quota = quotas.computeIfAbsent(
                key,
                ignored -> new Quota(
                        quotaKey,
                        capacity,
                        capacity
                )
        );

        synchronized (quota) {
            if (!quota.hasAvailableCapacity()) {
                return new QuotaConsumptionResult(
                        false,
                        quota.getAvailableCapacity()
                );
            }

            quota.consume();

            return new QuotaConsumptionResult(
                    true,
                    quota.getAvailableCapacity()
            );
        }
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
