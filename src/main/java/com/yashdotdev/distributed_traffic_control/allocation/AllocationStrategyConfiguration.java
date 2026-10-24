package com.yashdotdev.distributed_traffic_control.allocation;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(AllocationProperties.class)
public class AllocationStrategyConfiguration {

    @Bean
    @ConditionalOnMissingBean(AllocationStrategy.class)
    public AllocationStrategy allocationStrategy(
            AllocationProperties allocationProperties) {
        return new FixedAllocationStrategy(allocationProperties);
    }
}