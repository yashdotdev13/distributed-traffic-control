package com.yashdotdev.distributed_traffic_control.quota;

import com.yashdotdev.distributed_traffic_control.allocation.TokenBucketAlgorithm;
import com.yashdotdev.distributed_traffic_control.allocation.TrafficControlAlgorithm;
import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicy;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryQuotaCoordinator implements QuotaCoordinator {

    private final Map<String, Quota> quotas = new ConcurrentHashMap<>();

    private final Clock clock;

    private final TrafficControlAlgorithm trafficControlAlgorithm;

    public InMemoryQuotaCoordinator() {
        this(Clock.systemUTC());
    }

    public InMemoryQuotaCoordinator(Clock clock) {
        this.clock = clock;
        this.trafficControlAlgorithm =
                new TokenBucketAlgorithm(clock);
    }

    @Override
    public QuotaConsumptionResult tryConsume(
            QuotaKey quotaKey,
            TrafficPolicy policy
    ) {
        String key = buildKey(quotaKey);

        Quota quota = quotas.computeIfAbsent(
                key,
                ignored -> new Quota(
                        quotaKey,
                        policy.getCapacity(),
                        policy.getCapacity(),
                        clock.instant()
                )
        );

        synchronized (quota) {
            return trafficControlAlgorithm.tryConsume(
                    quota,
                    policy
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