package com.yashdotdev.distributed_traffic_control.allocation.algorithm;

import com.yashdotdev.distributed_traffic_control.policy.PolicyStatus;
import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicy;
import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicyType;
import com.yashdotdev.distributed_traffic_control.quota.Quota;
import com.yashdotdev.distributed_traffic_control.quota.QuotaConsumptionResult;
import com.yashdotdev.distributed_traffic_control.quota.QuotaKey;
import com.yashdotdev.distributed_traffic_control.traffic.TrafficSubject;
import com.yashdotdev.distributed_traffic_control.traffic.TrafficSubjectType;
import com.yashdotdev.distributed_traffic_control.traffic.algorithm.FixedWindowTrafficControlAlgorithm;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class FixedWindowTrafficControlAlgorithmTest {

    private static final Instant START_TIME =
            Instant.parse("2026-09-02T10:00:00Z");

    @Test
    void shouldAllowRequestWhenCapacityIsAvailable() {

        MutableClock clock = new MutableClock(START_TIME);

        FixedWindowTrafficControlAlgorithm algorithm =
                new FixedWindowTrafficControlAlgorithm(clock);

        TrafficPolicy policy = createPolicy(
                5,
                Duration.ofMinutes(1)
        );

        Quota quota = createQuota(
                policy,
                5,
                START_TIME
        );

        QuotaConsumptionResult result =
                algorithm.tryConsume(
                        quota,
                        policy
                );

        assertTrue(result.isConsumed());
        assertEquals(
                4,
                result.getRemainingCapacity()
        );
    }

    @Test
    void shouldRejectRequestWhenWindowCapacityIsExhausted() {

        MutableClock clock = new MutableClock(START_TIME);

        FixedWindowTrafficControlAlgorithm algorithm =
                new FixedWindowTrafficControlAlgorithm(clock);

        TrafficPolicy policy = createPolicy(
                2,
                Duration.ofMinutes(1)
        );

        Quota quota = createQuota(
                policy,
                2,
                START_TIME
        );

        algorithm.tryConsume(quota, policy);
        algorithm.tryConsume(quota, policy);

        QuotaConsumptionResult result =
                algorithm.tryConsume(
                        quota,
                        policy
                );

        assertFalse(result.isConsumed());
        assertEquals(
                0,
                result.getRemainingCapacity()
        );
    }

    @Test
    void shouldResetCapacityWhenWindowExpires() {

        MutableClock clock = new MutableClock(START_TIME);

        FixedWindowTrafficControlAlgorithm algorithm =
                new FixedWindowTrafficControlAlgorithm(clock);

        TrafficPolicy policy = createPolicy(
                3,
                Duration.ofMinutes(1)
        );

        Quota quota = createQuota(
                policy,
                3,
                START_TIME
        );

        algorithm.tryConsume(quota, policy);
        algorithm.tryConsume(quota, policy);
        algorithm.tryConsume(quota, policy);

        assertFalse(
                algorithm.tryConsume(
                        quota,
                        policy
                ).isConsumed()
        );

        clock.advanceSeconds(60);

        QuotaConsumptionResult result =
                algorithm.tryConsume(
                        quota,
                        policy
                );

        assertTrue(result.isConsumed());
        assertEquals(
                2,
                result.getRemainingCapacity()
        );
    }

    @Test
    void shouldNotResetCapacityBeforeWindowExpires() {

        MutableClock clock = new MutableClock(START_TIME);

        FixedWindowTrafficControlAlgorithm algorithm =
                new FixedWindowTrafficControlAlgorithm(clock);

        TrafficPolicy policy = createPolicy(
                2,
                Duration.ofMinutes(1)
        );

        Quota quota = createQuota(
                policy,
                2,
                START_TIME
        );

        algorithm.tryConsume(quota, policy);
        algorithm.tryConsume(quota, policy);

        clock.advanceSeconds(59);

        QuotaConsumptionResult result =
                algorithm.tryConsume(
                        quota,
                        policy
                );

        assertFalse(result.isConsumed());
        assertEquals(
                0,
                result.getRemainingCapacity()
        );
    }

    @Test
    void shouldAllowFullCapacityAfterWindowReset() {

        MutableClock clock = new MutableClock(START_TIME);

        FixedWindowTrafficControlAlgorithm algorithm =
                new FixedWindowTrafficControlAlgorithm(clock);

        TrafficPolicy policy = createPolicy(
                5,
                Duration.ofMinutes(1)
        );

        Quota quota = createQuota(
                policy,
                5,
                START_TIME
        );

        for (int i = 0; i < 5; i++) {
            assertTrue(
                    algorithm.tryConsume(
                            quota,
                            policy
                    ).isConsumed()
            );
        }

        assertEquals(
                0,
                quota.getAvailableCapacity()
        );

        clock.advanceSeconds(60);

        for (int i = 0; i < 5; i++) {
            assertTrue(
                    algorithm.tryConsume(
                            quota,
                            policy
                    ).isConsumed()
            );
        }

        assertEquals(
                0,
                quota.getAvailableCapacity()
        );
    }

    @Test
    void shouldRejectPolicyWithoutWindowDuration() {

        MutableClock clock = new MutableClock(START_TIME);

        FixedWindowTrafficControlAlgorithm algorithm =
                new FixedWindowTrafficControlAlgorithm(clock);

        TrafficPolicy policy = new TrafficPolicy(
                "fixed-window-policy",
                "Fixed Window Policy",
                TrafficPolicyType.FIXED_WINDOW,
                PolicyStatus.ACTIVE,
                5,
                1,
                null,
                START_TIME
        );

        Quota quota = createQuota(
                policy,
                5,
                START_TIME
        );

        assertThrows(
                NullPointerException.class,
                () -> algorithm.tryConsume(
                        quota,
                        policy
                )
        );
    }

    private TrafficPolicy createPolicy(
            long capacity,
            Duration windowDuration
    ) {
        return new TrafficPolicy(
                "fixed-window-policy",
                "Fixed Window Policy",
                TrafficPolicyType.FIXED_WINDOW,
                PolicyStatus.ACTIVE,
                capacity,
                1,
                windowDuration,
                START_TIME
        );
    }

    private Quota createQuota(
            TrafficPolicy policy,
            long availableCapacity,
            Instant createdAt
    ) {
        return new Quota(
                createQuotaKey(),
                policy.getCapacity(),
                availableCapacity,
                createdAt
        );
    }

    private QuotaKey createQuotaKey() {

        return new QuotaKey(
                "fixed-window-policy",
                new TrafficSubject(
                        "user-123",
                        TrafficSubjectType.USER
                ),
                "/api/orders"
        );
    }

    private static class MutableClock extends Clock {

        private Instant currentTime;

        private MutableClock(Instant currentTime) {
            this.currentTime = currentTime;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return currentTime;
        }

        private void advanceSeconds(long seconds) {
            currentTime = currentTime.plusSeconds(seconds);
        }
    }
}