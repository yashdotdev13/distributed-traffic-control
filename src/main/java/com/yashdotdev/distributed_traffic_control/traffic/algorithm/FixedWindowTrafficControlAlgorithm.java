package com.yashdotdev.distributed_traffic_control.traffic.algorithm;


import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicy;
import com.yashdotdev.distributed_traffic_control.quota.Quota;
import com.yashdotdev.distributed_traffic_control.quota.QuotaConsumptionResult;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public class FixedWindowTrafficControlAlgorithm
        implements TrafficControlAlgorithm {

    private final Clock clock;

    public FixedWindowTrafficControlAlgorithm(
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

        Duration windowDuration =
                Objects.requireNonNull(
                        policy.getWindowDuration(),
                        "windowDuration must not be null for FIXED_WINDOW"
                );

        Instant now = clock.instant();

        if (isWindowExpired(
                quota.getWindowStartedAt(),
                now,
                windowDuration
        )) {

            quota.resetWindow(now);
        }

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

    private boolean isWindowExpired(
            Instant windowStartedAt,
            Instant now,
            Duration windowDuration
    ) {

        return !now.isBefore(
                windowStartedAt.plus(windowDuration)
        );
    }
}
