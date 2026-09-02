package com.yashdotdev.distributed_traffic_control.allocation.algorithm;


import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicy;
import com.yashdotdev.distributed_traffic_control.quota.Quota;
import com.yashdotdev.distributed_traffic_control.quota.QuotaConsumptionResult;
import com.yashdotdev.distributed_traffic_control.quota.QuotaKey;
import com.yashdotdev.distributed_traffic_control.traffic.TrafficSubject;
import com.yashdotdev.distributed_traffic_control.traffic.TrafficSubjectType;
import com.yashdotdev.distributed_traffic_control.traffic.algorithm.TokenBucketTrafficControlAlgorithm;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TokenBucketTrafficControlAlgorithmTest {

    @Test
    void shouldConsumeTokenWhenCapacityIsAvailable() {

        MutableClock clock = new MutableClock(
                Instant.parse("2026-09-02T10:00:00Z")
        );

        TokenBucketTrafficControlAlgorithm algorithm =
                new TokenBucketTrafficControlAlgorithm(clock);

        TrafficPolicy policy = mock(TrafficPolicy.class);

        when(policy.getCapacity()).thenReturn(10L);
        when(policy.getRefillRate()).thenReturn(1L);

        Quota quota = new Quota(
                createQuotaKey(),
                10,
                10,
                clock.instant()
        );

        QuotaConsumptionResult result =
                algorithm.tryConsume(
                        quota,
                        policy
                );

        assertTrue(result.isConsumed());
        assertEquals(
                9,
                result.getRemainingCapacity()
        );
    }


    @Test
    void shouldRejectRequestWhenCapacityIsExhausted() {

        MutableClock clock = new MutableClock(
                Instant.parse("2026-09-02T10:00:00Z")
        );

        TokenBucketTrafficControlAlgorithm algorithm =
                new TokenBucketTrafficControlAlgorithm(clock);

        TrafficPolicy policy = mock(TrafficPolicy.class);

        when(policy.getCapacity()).thenReturn(10L);
        when(policy.getRefillRate()).thenReturn(1L);

        Quota quota = new Quota(
                createQuotaKey(),
                10,
                0,
                clock.instant()
        );

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
    void shouldRefillTokensAfterTimePasses() {

        MutableClock clock = new MutableClock(
                Instant.parse("2026-09-02T10:00:00Z")
        );

        TokenBucketTrafficControlAlgorithm algorithm =
                new TokenBucketTrafficControlAlgorithm(clock);

        TrafficPolicy policy = mock(TrafficPolicy.class);

        when(policy.getCapacity()).thenReturn(10L);
        when(policy.getRefillRate()).thenReturn(2L);

        Quota quota = new Quota(
                createQuotaKey(),
                10,
                0,
                clock.instant()
        );

        clock.advanceSeconds(3);

        QuotaConsumptionResult result =
                algorithm.tryConsume(
                        quota,
                        policy
                );

        assertTrue(result.isConsumed());

        assertEquals(
                5,
                result.getRemainingCapacity()
        );
    }


    @Test
    void shouldNotRefillBeyondMaximumCapacity() {

        MutableClock clock = new MutableClock(
                Instant.parse("2026-09-02T10:00:00Z")
        );

        TokenBucketTrafficControlAlgorithm algorithm =
                new TokenBucketTrafficControlAlgorithm(clock);

        TrafficPolicy policy = mock(TrafficPolicy.class);

        when(policy.getCapacity()).thenReturn(10L);
        when(policy.getRefillRate()).thenReturn(5L);

        Quota quota = new Quota(
                createQuotaKey(),
                10,
                8,
                clock.instant()
        );

        clock.advanceSeconds(5);

        QuotaConsumptionResult result =
                algorithm.tryConsume(
                        quota,
                        policy
                );

        assertTrue(result.isConsumed());

        assertEquals(
                9,
                result.getRemainingCapacity()
        );
    }


    @Test
    void shouldNotRefillWhenNoTimeHasPassed() {

        MutableClock clock = new MutableClock(
                Instant.parse("2026-09-02T10:00:00Z")
        );

        TokenBucketTrafficControlAlgorithm algorithm =
                new TokenBucketTrafficControlAlgorithm(clock);

        TrafficPolicy policy = mock(TrafficPolicy.class);

        when(policy.getCapacity()).thenReturn(10L);
        when(policy.getRefillRate()).thenReturn(5L);

        Quota quota = new Quota(
                createQuotaKey(),
                10,
                3,
                clock.instant()
        );

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
    void shouldRefillPartialCapacityCorrectly() {

        MutableClock clock = new MutableClock(
                Instant.parse("2026-09-02T10:00:00Z")
        );

        TokenBucketTrafficControlAlgorithm algorithm =
                new TokenBucketTrafficControlAlgorithm(clock);

        TrafficPolicy policy = mock(TrafficPolicy.class);

        when(policy.getCapacity()).thenReturn(10L);
        when(policy.getRefillRate()).thenReturn(2L);

        Quota quota = new Quota(
                createQuotaKey(),
                10,
                3,
                clock.instant()
        );

        clock.advanceSeconds(2);

        QuotaConsumptionResult result =
                algorithm.tryConsume(
                        quota,
                        policy
                );

        assertTrue(result.isConsumed());

        assertEquals(
                6,
                result.getRemainingCapacity()
        );
    }


    private QuotaKey createQuotaKey() {

        return new QuotaKey(
                "test-policy",
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

        public void advanceSeconds(
                long seconds
        ) {
            currentTime =
                    currentTime.plusSeconds(seconds);
        }
    }
}