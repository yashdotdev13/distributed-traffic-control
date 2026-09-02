package com.yashdotdev.distributed_traffic_control.allocation.algorithm;


import com.yashdotdev.distributed_traffic_control.policy.PolicyStatus;
import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicy;
import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicyType;
import com.yashdotdev.distributed_traffic_control.quota.Quota;
import com.yashdotdev.distributed_traffic_control.quota.QuotaConsumptionResult;
import com.yashdotdev.distributed_traffic_control.quota.QuotaKey;
import com.yashdotdev.distributed_traffic_control.traffic.TrafficSubject;
import com.yashdotdev.distributed_traffic_control.traffic.TrafficSubjectType;
import com.yashdotdev.distributed_traffic_control.traffic.algorithm.SlidingWindowTrafficControlAlgorithm;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class SlidingWindowTrafficControlAlgorithmTest {

    private static final Instant START_TIME =
            Instant.parse("2026-09-02T10:00:00Z");

    @Test
    void shouldAllowRequestWhenCapacityIsAvailable() {

        MutableClock clock = new MutableClock(START_TIME);

        SlidingWindowTrafficControlAlgorithm algorithm =
                new SlidingWindowTrafficControlAlgorithm(clock);

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

        SlidingWindowTrafficControlAlgorithm algorithm =
                new SlidingWindowTrafficControlAlgorithm(clock);

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
    void shouldAllowRequestWhenOldRequestLeavesSlidingWindow() {

        MutableClock clock = new MutableClock(START_TIME);

        SlidingWindowTrafficControlAlgorithm algorithm =
                new SlidingWindowTrafficControlAlgorithm(clock);

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

        clock.advanceSeconds(10);
        algorithm.tryConsume(quota, policy);

        clock.advanceSeconds(10);
        algorithm.tryConsume(quota, policy);

        assertFalse(
                algorithm.tryConsume(
                        quota,
                        policy
                ).isConsumed()
        );

        /*
         * The first request happened at 10:00:00.
         * At 10:01:01 it is outside the 60-second
         * sliding window.
         */
        clock.advanceSeconds(41);

        QuotaConsumptionResult result =
                algorithm.tryConsume(
                        quota,
                        policy
                );

        assertTrue(result.isConsumed());
        assertEquals(
                0,
                result.getRemainingCapacity()
        );
    }

    @Test
    void shouldNotRemoveRequestBeforeWindowExpires() {

        MutableClock clock = new MutableClock(START_TIME);

        SlidingWindowTrafficControlAlgorithm algorithm =
                new SlidingWindowTrafficControlAlgorithm(clock);

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

        clock.advanceSeconds(30);

        algorithm.tryConsume(quota, policy);

        clock.advanceSeconds(29);

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
    void shouldAllowRequestsAsOldEntriesExpireIndividually() {

        MutableClock clock = new MutableClock(START_TIME);

        SlidingWindowTrafficControlAlgorithm algorithm =
                new SlidingWindowTrafficControlAlgorithm(clock);

        TrafficPolicy policy = createPolicy(
                3,
                Duration.ofMinutes(1)
        );

        Quota quota = createQuota(
                policy,
                3,
                START_TIME
        );

        /*
         * Request 1 -> 10:00:00
         */
        algorithm.tryConsume(quota, policy);

        /*
         * Request 2 -> 10:00:20
         */
        clock.advanceSeconds(20);
        algorithm.tryConsume(quota, policy);

        /*
         * Request 3 -> 10:00:40
         */
        clock.advanceSeconds(20);
        algorithm.tryConsume(quota, policy);

        /*
         * At 10:01:01:
         * Request 1 is outside the sliding window.
         * Requests 2 and 3 are still inside.
         */
        clock.advanceSeconds(21);

        QuotaConsumptionResult result =
                algorithm.tryConsume(
                        quota,
                        policy
                );

        assertTrue(result.isConsumed());
        assertEquals(
                0,
                result.getRemainingCapacity()
        );
    }

    @Test
    void shouldRejectPolicyWithoutWindowDuration() {

        MutableClock clock = new MutableClock(START_TIME);

        SlidingWindowTrafficControlAlgorithm algorithm =
                new SlidingWindowTrafficControlAlgorithm(clock);

        TrafficPolicy policy = new TrafficPolicy(
                "sliding-window-policy",
                "Sliding Window Policy",
                TrafficPolicyType.SLIDING_WINDOW,
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
                "sliding-window-policy",
                "Sliding Window Policy",
                TrafficPolicyType.SLIDING_WINDOW,
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
                "sliding-window-policy",
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