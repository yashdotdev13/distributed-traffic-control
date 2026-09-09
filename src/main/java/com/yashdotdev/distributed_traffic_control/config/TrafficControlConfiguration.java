package com.yashdotdev.distributed_traffic_control.config;

import com.yashdotdev.distributed_traffic_control.allocation.AllocationProperties;
import com.yashdotdev.distributed_traffic_control.allocation.AllocationStrategy;
import com.yashdotdev.distributed_traffic_control.allocation.CapacityAllocator;
import com.yashdotdev.distributed_traffic_control.allocation.FixedAllocationStrategy;
import com.yashdotdev.distributed_traffic_control.allocation.InMemoryCapacityAllocator;
import com.yashdotdev.distributed_traffic_control.lease.*;
import com.yashdotdev.distributed_traffic_control.monitoring.TrafficControlMetrics;
import com.yashdotdev.distributed_traffic_control.policy.*;
import com.yashdotdev.distributed_traffic_control.quota.InMemoryQuotaCoordinator;
import com.yashdotdev.distributed_traffic_control.quota.QuotaCoordinator;
import com.yashdotdev.distributed_traffic_control.traffic.DefaultTrafficControlService;
import com.yashdotdev.distributed_traffic_control.traffic.TrafficControlService;
import com.yashdotdev.distributed_traffic_control.traffic.TrafficDecisionEngine;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;

@Configuration
@EnableConfigurationProperties(AllocationProperties.class)
public class TrafficControlConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public TrafficPolicy defaultTrafficPolicy(Clock clock) {
        return new TrafficPolicy("default-policy", "Default Traffic Policy", TrafficPolicyType.TOKEN_BUCKET, PolicyStatus.ACTIVE, 100, 10, clock.instant());
    }

    @Bean
    public InMemoryPolicyProvider policyProvider(TrafficPolicy defaultTrafficPolicy) {
        return new InMemoryPolicyProvider(defaultTrafficPolicy);
    }

    @Bean
    public PolicyManagementService policyManagementService(InMemoryPolicyProvider policyProvider) {
        return new DefaultPolicyManagementService(policyProvider);
    }


    @Bean
    public QuotaCoordinator quotaCoordinator(Clock clock) {
        return new InMemoryQuotaCoordinator(clock);
    }

    @Bean
    public LeaseCoordinator leaseCoordinator(StringRedisTemplate redisTemplate, Clock clock) {
        return new RedisLeaseCoordinator(redisTemplate, clock);
    }

    @Bean
    public LeaseStore leaseStore() {
        return new InMemoryLeaseStore();
    }

    @Bean
    public FixedAllocationStrategy allocationStrategy(AllocationProperties allocationProperties) {
        return new FixedAllocationStrategy(allocationProperties);
    }

    @Bean
    public CapacityAllocator capacityAllocator(LeaseCoordinator leaseCoordinator, AllocationStrategy allocationStrategy, AllocationProperties allocationProperties, LeaseStore leaseStore, Clock clock) {
        return new InMemoryCapacityAllocator(leaseCoordinator, allocationStrategy, allocationProperties, leaseStore, clock);
    }

    @Bean
    public TrafficDecisionEngine trafficDecisionEngine(PolicyProvider policyProvider, QuotaCoordinator quotaCoordinator, CapacityAllocator capacityAllocator, TrafficControlMetrics metrics) {
        return new TrafficDecisionEngine(policyProvider, quotaCoordinator, capacityAllocator, metrics);
    }

    @Bean
    public TrafficControlService trafficControlService(TrafficDecisionEngine trafficDecisionEngine) {
        return new DefaultTrafficControlService(trafficDecisionEngine);
    }

    @Bean
    public TrafficControlMetrics trafficControlMetrics(MeterRegistry meterRegistry) {
        return new TrafficControlMetrics(meterRegistry);
    }

}