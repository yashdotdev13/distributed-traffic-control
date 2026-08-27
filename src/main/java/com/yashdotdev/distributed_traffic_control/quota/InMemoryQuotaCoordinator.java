package com.yashdotdev.distributed_traffic_control.quota;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryQuotaCoordinator implements QuotaCoordinator {

    private final Map<String, Quota> quotas = new ConcurrentHashMap<>();

    private final Clock clock;

    public InMemoryQuotaCoordinator() {
        this(Clock.systemUTC());
    }
    public InMemoryQuotaCoordinator(Clock clock){
        this.clock = clock;
    }

    @Override
    public QuotaConsumptionResult tryConsume(
            QuotaKey quotaKey,
            long capacity,
            long refillRate
    ) {
        String key = buildKey(quotaKey);

        Quota quota = quotas.computeIfAbsent(
                key,
                ignored -> new Quota(
                        quotaKey,
                        capacity,
                        capacity,
                        clock.instant()
                )
        );

        synchronized (quota) {
            refillQuota(quota, refillRate);

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

    private void refillQuota(
            Quota quota,
            long refillRate
    ) {
        Instant now = clock.instant();

        long elapsedSeconds = Duration.between(
                quota.getLastRefilledAt(),
                now
        ).getSeconds();

        if (elapsedSeconds <= 0) {
            return;
        }

        long tokensToAdd = elapsedSeconds * refillRate;

        quota.refill(tokensToAdd, now);
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