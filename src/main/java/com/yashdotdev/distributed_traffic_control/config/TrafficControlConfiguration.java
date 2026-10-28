package com.yashdotdev.distributed_traffic_control.config;

import com.yashdotdev.distributed_traffic_control.allocation.AllocationProperties;
import com.yashdotdev.distributed_traffic_control.allocation.AllocationStrategy;
import com.yashdotdev.distributed_traffic_control.allocation.AllocationStrategyConfiguration;
import com.yashdotdev.distributed_traffic_control.allocation.CapacityAllocator;
import com.yashdotdev.distributed_traffic_control.allocation.InMemoryCapacityAllocator;
import com.yashdotdev.distributed_traffic_control.lease.DistributedTokenBucket;
import com.yashdotdev.distributed_traffic_control.lease.InMemoryLeaseStore;
import com.yashdotdev.distributed_traffic_control.lease.LeaseCoordinator;
import com.yashdotdev.distributed_traffic_control.lease.LeaseStore;
import com.yashdotdev.distributed_traffic_control.lease.RedisLeaseCoordinator;
import com.yashdotdev.distributed_traffic_control.monitoring.TrafficControlMetrics;
import com.yashdotdev.distributed_traffic_control.policy.*;
import com.yashdotdev.distributed_traffic_control.quota.InMemoryQuotaCoordinator;
import com.yashdotdev.distributed_traffic_control.quota.QuotaCoordinator;
import com.yashdotdev.distributed_traffic_control.traffic.DefaultTrafficControlService;
import com.yashdotdev.distributed_traffic_control.traffic.TrafficControlService;
import com.yashdotdev.distributed_traffic_control.traffic.TrafficDecisionEngine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;

@Configuration
@Import(AllocationStrategyConfiguration.class)
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
    public RedisPolicyStore policyStore(
            StringRedisTemplate redisTemplate,
            TrafficPolicy defaultTrafficPolicy) {

        RedisPolicyStore policyStore = new RedisPolicyStore(redisTemplate);
        policyStore.initializeDefaultPolicy(defaultTrafficPolicy);

        return policyStore;
    }

    @Bean
    public PolicyManagementService policyManagementService(PolicyStore policyStore) {
        return new DefaultPolicyManagementService(policyStore);
    }


    @Bean
    public QuotaCoordinator quotaCoordinator(Clock clock) {
        return new InMemoryQuotaCoordinator(clock);
    }

    @Bean
    public LeaseCoordinator leaseCoordinator(
            StringRedisTemplate redisTemplate,
            Clock clock,
            ObservationRegistry observationRegistry) {
        return new RedisLeaseCoordinator(
                redisTemplate,
                clock,
                observationRegistry);
    }

    @Bean
    public LeaseStore leaseStore() {
        return new InMemoryLeaseStore();
    }

    @Bean
    public CapacityAllocator capacityAllocator(LeaseCoordinator leaseCoordinator, AllocationStrategy allocationStrategy, AllocationProperties allocationProperties, LeaseStore leaseStore, Clock clock) {
        return new InMemoryCapacityAllocator(leaseCoordinator, allocationStrategy, allocationProperties, leaseStore, clock);
    }

    @Bean
    public TrafficDecisionEngine trafficDecisionEngine(
            PolicyProvider policyProvider,
            QuotaCoordinator quotaCoordinator,
            CapacityAllocator capacityAllocator,
            TrafficControlMetrics trafficControlMetrics,
            ObservationRegistry observationRegistry,
            DistributedTokenBucket distributedTokenBucket) {
        return new TrafficDecisionEngine(
                policyProvider,
                quotaCoordinator,
                capacityAllocator,
                trafficControlMetrics,
                observationRegistry,
                distributedTokenBucket);
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