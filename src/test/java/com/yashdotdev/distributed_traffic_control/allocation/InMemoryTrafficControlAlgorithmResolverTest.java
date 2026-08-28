package com.yashdotdev.distributed_traffic_control.allocation;

import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicyType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryTrafficControlAlgorithmResolverTest {

    @Test
    void shouldResolveRegisteredAlgorithm() {

        TrafficControlAlgorithm tokenBucketAlgorithm =
                new TokenBucketAlgorithm(
                        Clock.systemUTC()
                );

        TrafficControlAlgorithmResolver resolver =
                new InMemoryTrafficControlAlgorithmResolver(
                        Map.of(
                                TrafficPolicyType.TOKEN_BUCKET,
                                tokenBucketAlgorithm
                        )
                );

        TrafficControlAlgorithm resolvedAlgorithm =
                resolver.resolve(
                        TrafficPolicyType.TOKEN_BUCKET
                );

        assertSame(
                tokenBucketAlgorithm,
                resolvedAlgorithm
        );
    }
    @Test
    void shouldThrowExceptionWhenAlgorithmIsNotRegistered() {

        TrafficControlAlgorithmResolver resolver =
                new InMemoryTrafficControlAlgorithmResolver(
                        Map.of()
                );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> resolver.resolve(
                                TrafficPolicyType.TOKEN_BUCKET
                        )
                );

        assertEquals(
                "No traffic control algorithm registered for policy type: TOKEN_BUCKET",
                exception.getMessage()
        );
    }
}