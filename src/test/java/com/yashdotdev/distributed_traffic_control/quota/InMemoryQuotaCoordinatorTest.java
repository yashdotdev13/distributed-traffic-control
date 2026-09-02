package com.yashdotdev.distributed_traffic_control.quota;


import com.yashdotdev.distributed_traffic_control.policy.PolicyStatus;
import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicy;
import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicyType;
import com.yashdotdev.distributed_traffic_control.traffic.TrafficSubject;
import com.yashdotdev.distributed_traffic_control.traffic.TrafficSubjectType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryQuotaCoordinatorTest {

    private static final Instant START_TIME =
            Instant.parse("2026-09-02T10:00:00Z");

    @Test
    void shouldResolveAndExecuteTokenBucketAlgorithm() {

        MutableClock clock = new MutableClock(START_TIME);

        InMemoryQuotaCoordinator coordinator =
                new InMemoryQuotaCoordinator(clock);

        TrafficPolicy policy = new TrafficPolicy(
                "token-bucket-policy",
                "Token Bucket Policy",
                TrafficPolicyType.TOKEN_BUCKET,
                PolicyStatus.ACTIVE,
                3,
                1,
                START_TIME
        );

        QuotaKey quotaKey = createQuotaKey(
                "token-bucket-policy"
        );

        QuotaConsumptionResult first =
                coordinator.tryConsume(
                        quotaKey,
                        policy
                );

        QuotaConsumptionResult second =
                coordinator.tryConsume(
                        quotaKey,
                        policy
                );

        QuotaConsumptionResult third =
                coordinator.tryConsume(
                        quotaKey,
                        policy
                );

        QuotaConsumptionResult fourth =
                coordinator.tryConsume(
                        quotaKey,
                        policy
                );

        assertTrue(first.isConsumed());
        assertEquals(2, first.getRemainingCapacity());

        assertTrue(second.isConsumed());
        assertEquals(1, second.getRemainingCapacity());

        assertTrue(third.isConsumed());
        assertEquals(0, third.getRemainingCapacity());

        assertFalse(fourth.isConsumed());
        assertEquals(0, fourth.getRemainingCapacity());
    }

    @Test
    void shouldResolveAndExecuteFixedWindowAlgorithm() {

        MutableClock clock = new MutableClock(START_TIME);

        InMemoryQuotaCoordinator coordinator =
                new InMemoryQuotaCoordinator(clock);

        TrafficPolicy policy = new TrafficPolicy(
                "fixed-window-policy",
                "Fixed Window Policy",
                TrafficPolicyType.FIXED_WINDOW,
                PolicyStatus.ACTIVE,
                2,
                1,
                Duration.ofMinutes(1),
                START_TIME
        );

        QuotaKey quotaKey = createQuotaKey(
                "fixed-window-policy"
        );

        QuotaConsumptionResult first =
                coordinator.tryConsume(
                        quotaKey,
                        policy
                );

        QuotaConsumptionResult second =
                coordinator.tryConsume(
                        quotaKey,
                        policy
                );

        QuotaConsumptionResult third =
                coordinator.tryConsume(
                        quotaKey,
                        policy
                );

        assertTrue(first.isConsumed());
        assertEquals(1, first.getRemainingCapacity());

        assertTrue(second.isConsumed());
        assertEquals(0, second.getRemainingCapacity());

        assertFalse(third.isConsumed());
        assertEquals(0, third.getRemainingCapacity());

        clock.advanceSeconds(60);

        QuotaConsumptionResult afterWindow =
                coordinator.tryConsume(
                        quotaKey,
                        policy
                );

        assertTrue(afterWindow.isConsumed());
        assertEquals(1, afterWindow.getRemainingCapacity());
    }

    @Test
    void shouldResolveAndExecuteSlidingWindowAlgorithm() {

        MutableClock clock = new MutableClock(START_TIME);

        InMemoryQuotaCoordinator coordinator =
                new InMemoryQuotaCoordinator(clock);

        TrafficPolicy policy = new TrafficPolicy(
                "sliding-window-policy",
                "Sliding Window Policy",
                TrafficPolicyType.SLIDING_WINDOW,
                PolicyStatus.ACTIVE,
                2,
                1,
                Duration.ofMinutes(1),
                START_TIME
        );

        QuotaKey quotaKey = createQuotaKey(
                "sliding-window-policy"
        );

        QuotaConsumptionResult first =
                coordinator.tryConsume(
                        quotaKey,
                        policy
                );

        clock.advanceSeconds(20);

        QuotaConsumptionResult second =
                coordinator.tryConsume(
                        quotaKey,
                        policy
                );

        QuotaConsumptionResult third =
                coordinator.tryConsume(
                        quotaKey,
                        policy
                );

        assertTrue(first.isConsumed());
        assertEquals(1, first.getRemainingCapacity());

        assertTrue(second.isConsumed());
        assertEquals(0, second.getRemainingCapacity());

        assertFalse(third.isConsumed());
        assertEquals(0, third.getRemainingCapacity());

        /*
         * At this point:
         *
         * Request 1 -> 10:00:00
         * Request 2 -> 10:00:20
         *
         * Move to 10:01:01.
         * Request 1 is now outside the sliding window,
         * while request 2 is still inside.
         */
        clock.advanceSeconds(41);

        QuotaConsumptionResult afterOldRequestExpires =
                coordinator.tryConsume(
                        quotaKey,
                        policy
                );

        assertTrue(afterOldRequestExpires.isConsumed());
        assertEquals(
                0,
                afterOldRequestExpires.getRemainingCapacity()
        );
    }

    private QuotaKey createQuotaKey(
            String policyId
    ) {

        return new QuotaKey(
                policyId,
                new TrafficSubject(
                        "user-123",
                        TrafficSubjectType.USER
                ),
                "/api/orders"
        );
    }

    private static class MutableClock extends Clock {

        private Instant currentTime;

        private MutableClock(
                Instant currentTime
        ) {
            this.currentTime = currentTime;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(
                ZoneId zone
        ) {
            return this;
        }

        @Override
        public Instant instant() {
            return currentTime;
        }

        private void advanceSeconds(
                long seconds
        ) {
            currentTime =
                    currentTime.plusSeconds(seconds);
        }
    }
}
