package com.yashdotdev.distributed_traffic_control.config;

import com.yashdotdev.distributed_traffic_control.lease.GlobalCapacityKey;
import com.yashdotdev.distributed_traffic_control.lease.LeaseCoordinator;
import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicy;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class GlobalCapacityBootstrap implements ApplicationRunner {

    private static final String DEFAULT_RESOURCE = "/api/orders";

    private final LeaseCoordinator leaseCoordinator;
    private final TrafficPolicy defaultTrafficPolicy;

    public GlobalCapacityBootstrap(LeaseCoordinator leaseCoordinator, TrafficPolicy defaultTrafficPolicy) {
        this.leaseCoordinator = leaseCoordinator;
        this.defaultTrafficPolicy = defaultTrafficPolicy;
    }

    @Override
    public void run(ApplicationArguments args) {
        GlobalCapacityKey capacityKey = new GlobalCapacityKey(defaultTrafficPolicy.getPolicyId(), DEFAULT_RESOURCE);
        leaseCoordinator.registerCapacity(capacityKey, defaultTrafficPolicy.getCapacity());
    }
}