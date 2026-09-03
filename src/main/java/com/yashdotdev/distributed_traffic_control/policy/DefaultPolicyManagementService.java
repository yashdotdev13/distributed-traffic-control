package com.yashdotdev.distributed_traffic_control.policy;

import java.util.Objects;

public class DefaultPolicyManagementService implements PolicyManagementService{


    private final InMemoryPolicyProvider policyProvider;

    public DefaultPolicyManagementService(
            InMemoryPolicyProvider policyProvider
    ) {
        this.policyProvider = Objects.requireNonNull(
                policyProvider,
                "policyProvider must not be null"
        );
    }

    @Override
    public TrafficPolicy registerPolicy(String resource, TrafficPolicy policy) {

        policyProvider.registerPolicy(resource, policy);
        return policy;
    }

    @Override
    public void removePolicy(String resource) {
        policyProvider.removePolicy(resource);
    }

    @Override
    public void setDefaultPolicy(TrafficPolicy policy) {
        policyProvider.setDefaultPolicy(policy);
    }
}
