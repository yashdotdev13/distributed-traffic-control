package com.yashdotdev.distributed_traffic_control.allocation;

import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicy;
import com.yashdotdev.distributed_traffic_control.quota.Quota;
import com.yashdotdev.distributed_traffic_control.quota.QuotaConsumptionResult;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public class TokenBucketAlgorithm implements TrafficControlAlgorithm {

    private final Clock clock;

    public TokenBucketAlgorithm(Clock clock) {
        this.clock = clock;
    }

    @Override
    public QuotaConsumptionResult tryConsume(
            Quota quota,
            TrafficPolicy policy
    ) {
        refillQuota(quota, policy.getRefillRate());

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
}
