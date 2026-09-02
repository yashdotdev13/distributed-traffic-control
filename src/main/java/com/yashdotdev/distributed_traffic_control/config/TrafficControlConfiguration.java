package com.yashdotdev.distributed_traffic_control.config;

import com.yashdotdev.distributed_traffic_control.allocation.AllocationProperties;
import com.yashdotdev.distributed_traffic_control.allocation.AllocationStrategy;
import com.yashdotdev.distributed_traffic_control.allocation.CapacityAllocator;
import com.yashdotdev.distributed_traffic_control.allocation.FixedAllocationStrategy;
import com.yashdotdev.distributed_traffic_control.allocation.InMemoryCapacityAllocator;
import com.yashdotdev.distributed_traffic_control.lease.InMemoryLeaseCoordinator;
import com.yashdotdev.distributed_traffic_control.lease.InMemoryLeaseStore;
import com.yashdotdev.distributed_traffic_control.lease.LeaseCoordinator;
import com.yashdotdev.distributed_traffic_control.lease.LeaseStore;
import com.yashdotdev.distributed_traffic_control.policy.InMemoryPolicyProvider;
import com.yashdotdev.distributed_traffic_control.policy.PolicyProvider;
import com.yashdotdev.distributed_traffic_control.policy.PolicyStatus;
import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicy;
import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicyType;
import com.yashdotdev.distributed_traffic_control.quota.InMemoryQuotaCoordinator;
import com.yashdotdev.distributed_traffic_control.quota.QuotaCoordinator;
import com.yashdotdev.distributed_traffic_control.traffic.DefaultTrafficControlService;
import com.yashdotdev.distributed_traffic_control.traffic.TrafficControlService;
import com.yashdotdev.distributed_traffic_control.traffic.TrafficDecisionEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class TrafficControlConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public TrafficPolicy defaultTrafficPolicy(
            Clock clock
    ) {
        return new TrafficPolicy(
                "default-policy",
                "Default Traffic Policy",
                TrafficPolicyType.TOKEN_BUCKET,
                PolicyStatus.ACTIVE,
                100,
                10,
                clock.instant()
        );
    }

    @Bean
    public PolicyProvider policyProvider(
            TrafficPolicy defaultTrafficPolicy
    ) {
        return new InMemoryPolicyProvider(
                defaultTrafficPolicy
        );
    }


    @Bean
    public QuotaCoordinator quotaCoordinator(
            Clock clock
    ) {
        return new InMemoryQuotaCoordinator(
                clock
        );
    }

    @Bean
    public LeaseCoordinator leaseCoordinator(
            Clock clock
    ) {
        return new InMemoryLeaseCoordinator(
                clock
        );
    }

    @Bean
    public LeaseStore leaseStore() {
        return new InMemoryLeaseStore();
    }

    @Bean
    public AllocationStrategy allocationStrategy() {
        return new FixedAllocationStrategy();
    }

    @Bean
    public AllocationProperties allocationProperties() {

        AllocationProperties properties =
                new AllocationProperties();

        properties.setNodeId("local-node");

        return properties;
    }

    @Bean
    public CapacityAllocator capacityAllocator(
            LeaseCoordinator leaseCoordinator,
            AllocationStrategy allocationStrategy,
            AllocationProperties allocationProperties,
            LeaseStore leaseStore,
            Clock clock
    ) {
        return new InMemoryCapacityAllocator(
                leaseCoordinator,
                allocationStrategy,
                allocationProperties,
                leaseStore,
                clock
        );
    }

    @Bean
    public TrafficDecisionEngine trafficDecisionEngine(
            PolicyProvider policyProvider,
            QuotaCoordinator quotaCoordinator,
            CapacityAllocator capacityAllocator
    ) {
        return new TrafficDecisionEngine(
                policyProvider,
                quotaCoordinator,
                capacityAllocator
        );
    }

    @Bean
    public TrafficControlService trafficControlService(
            TrafficDecisionEngine trafficDecisionEngine
    ) {
        return new DefaultTrafficControlService(
                trafficDecisionEngine
        );
    }
}