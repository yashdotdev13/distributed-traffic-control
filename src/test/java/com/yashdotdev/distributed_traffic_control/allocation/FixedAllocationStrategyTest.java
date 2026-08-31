package com.yashdotdev.distributed_traffic_control.allocation;

import com.yashdotdev.distributed_traffic_control.policy.PolicyStatus;
import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicy;
import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicyType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FixedAllocationStrategyTest {

    @Test
    void shouldUsePolicyCapacityAsAllocationCapacity() {

        TrafficPolicy policy = new TrafficPolicy(
                "test-policy",
                "Test Policy",
                TrafficPolicyType.TOKEN_BUCKET,
                PolicyStatus.ACTIVE,
                100,
                10,
                Instant.parse("2026-08-28T10:00:00Z")
        );

        AllocationStrategy strategy =
                new FixedAllocationStrategy();

        long allocatedCapacity =
                strategy.determineCapacity(policy);

        assertEquals(
                100,
                allocatedCapacity
        );
    }

    @Test
    void shouldRejectNullPolicy() {

        AllocationStrategy strategy =
                new FixedAllocationStrategy();

        assertThrows(
                IllegalArgumentException.class,
                () -> strategy.determineCapacity(null)
        );
    }
}