package com.yashdotdev.distributed_traffic_control.allocation;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class AllocationPropertiesValidationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void shouldAcceptValidConfiguration() {
        contextRunner
                .withPropertyValues(
                        "allocation.node-id=node-1",
                        "allocation.lease-duration=PT30S",
                        "allocation.lease-capacity=10"
                )
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void shouldRejectBlankNodeId() {
        contextRunner
                .withPropertyValues(
                        "allocation.node-id=",
                        "allocation.lease-duration=PT30S",
                        "allocation.lease-capacity=10"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("nodeId")
                            .hasStackTraceContaining("must not be blank");
                });
    }

    @Test
    void shouldRejectNonPositiveLeaseCapacity() {
        contextRunner
                .withPropertyValues(
                        "allocation.node-id=node-1",
                        "allocation.lease-duration=PT30S",
                        "allocation.lease-capacity=0"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("leaseCapacity")
                            .hasStackTraceContaining("must be greater than or equal to 1");
                });
    }

    @Test
    void shouldRejectNonPositiveLeaseDuration() {
        contextRunner
                .withPropertyValues(
                        "allocation.node-id=node-1",
                        "allocation.lease-duration=PT0S",
                        "allocation.lease-capacity=10"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("leaseDuration")
                            .hasStackTraceContaining("must be greater than zero");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AllocationProperties.class)
    static class TestConfiguration {
    }
}