package com.yashdotdev.distributed_traffic_control.traffic.algorithm;

import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicy;
import com.yashdotdev.distributed_traffic_control.quota.Quota;
import com.yashdotdev.distributed_traffic_control.quota.QuotaConsumptionResult;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class SlidingWindowTrafficControlAlgorithm
        implements TrafficControlAlgorithm {

    private final Clock clock;

    private final Map<Quota, Deque<Instant>> requestTimestamps =
            new ConcurrentHashMap<>();

    public SlidingWindowTrafficControlAlgorithm(
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
                        "windowDuration must not be null for SLIDING_WINDOW"
                );

        Instant now = clock.instant();

        Deque<Instant> timestamps =
                requestTimestamps.computeIfAbsent(
                        quota,
                        ignored -> new ArrayDeque<>()
                );

        synchronized (timestamps) {

            removeExpiredRequests(
                    timestamps,
                    now,
                    windowDuration
            );

            if (timestamps.size() >= policy.getCapacity()) {
                return new QuotaConsumptionResult(
                        false,
                        0
                );
            }

            timestamps.addLast(now);

            long remainingCapacity =
                    policy.getCapacity()
                            - timestamps.size();

            return new QuotaConsumptionResult(
                    true,
                    remainingCapacity
            );
        }
    }

    private void removeExpiredRequests(
            Deque<Instant> timestamps,
            Instant now,
            Duration windowDuration
    ) {

        Instant windowStart =
                now.minus(windowDuration);

        while (!timestamps.isEmpty()
                && !timestamps.peekFirst().isAfter(windowStart)) {

            timestamps.removeFirst();
        }
    }
}