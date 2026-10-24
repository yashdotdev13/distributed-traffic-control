package com.yashdotdev.distributed_traffic_control.allocation;

import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class AllocationStrategyConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            org.springframework.boot.autoconfigure.AutoConfigurations.of(
                                    AllocationStrategyConfiguration.class));

    @Test
    void shouldRegisterFixedAllocationStrategyByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(AllocationStrategy.class);
            assertThat(context.getBean(AllocationStrategy.class))
                    .isInstanceOf(FixedAllocationStrategy.class);
        });
    }

    @Test
    void shouldUseCustomAllocationStrategyWhenProvided() {
        contextRunner
                .withUserConfiguration(CustomStrategyConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AllocationStrategy.class);
                    assertThat(context.getBean(AllocationStrategy.class))
                            .isInstanceOf(CustomAllocationStrategy.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomStrategyConfiguration {

        @Bean
        AllocationStrategy customAllocationStrategy() {
            return new CustomAllocationStrategy();
        }
    }

    static class CustomAllocationStrategy implements AllocationStrategy {

        @Override
        public long determineCapacity(TrafficPolicy policy) {
            return 1;
        }
    }
}