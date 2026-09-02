package com.yashdotdev.distributed_traffic_control.traffic.algorithm;

import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicy;
import com.yashdotdev.distributed_traffic_control.quota.Quota;
import com.yashdotdev.distributed_traffic_control.quota.QuotaConsumptionResult;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public class TokenBucketTrafficControlAlgorithm
        implements TrafficControlAlgorithm {

    private final Clock clock;

    public TokenBucketTrafficControlAlgorithm(
            Clock clock
    ) {
        this.clock = Objects.requireNonNull(
                clock,
                "clock must not be null"
        );
    }

    @Override
    public QuotaConsumptionResult tryConsume(
            Quota quota,
            TrafficPolicy policy
    ) {

        Objects.requireNonNull(
                quota,
                "quota must not be null"
        );

        Objects.requireNonNull(
                policy,
                "policy must not be null"
        );

        Instant now = clock.instant();

        refillTokens(
                quota,
                policy,
                now
        );

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

    private void refillTokens(
            Quota quota,
            TrafficPolicy policy,
            Instant now
    ) {

        Instant lastRefilledAt =
                quota.getLastRefilledAt();

        long elapsedSeconds =
                Duration.between(
                        lastRefilledAt,
                        now
                ).getSeconds();

        if (elapsedSeconds <= 0) {
            return;
        }

        long tokensToAdd =
                elapsedSeconds * policy.getRefillRate();

        if (tokensToAdd <= 0) {
            return;
        }

        quota.refill(
                tokensToAdd,
                now
        );
    }
}