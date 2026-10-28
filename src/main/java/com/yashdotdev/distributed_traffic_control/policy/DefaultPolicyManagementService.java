package com.yashdotdev.distributed_traffic_control.policy;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class DefaultPolicyManagementService implements PolicyManagementService {

    private final PolicyStore policyStore;

    public DefaultPolicyManagementService(PolicyStore policyStore) {
        this.policyStore = Objects.requireNonNull(
                policyStore,
                "policyStore must not be null"
        );
    }

    @Override
    public TrafficPolicy registerPolicy(String resource, TrafficPolicy policy) {
        policyStore.registerPolicy(resource, policy);
        return policy;
    }

    @Override
    public Optional<TrafficPolicy> findPolicy(String resource) {
        return policyStore.findPolicy(resource);
    }

    @Override
    public List<PolicyRegistration> findAllPolicies() {
        return policyStore.findAllPolicies();
    }

    @Override
    public TrafficPolicy updatePolicy(String resource, TrafficPolicy policy) {
        Optional<TrafficPolicy> existingPolicy = policyStore.findPolicy(resource);

        if (existingPolicy.isEmpty()) {
            throw new IllegalArgumentException(
                    "No policy registered for resource: " + resource
            );
        }

        policyStore.registerPolicy(resource, policy);
        return policy;
    }

    @Override
    public TrafficPolicy activatePolicy(String resource) {
        return changePolicyStatus(resource, PolicyStatus.ACTIVE);
    }

    @Override
    public TrafficPolicy deactivatePolicy(String resource) {
        return changePolicyStatus(resource, PolicyStatus.INACTIVE);
    }

    private TrafficPolicy changePolicyStatus(String resource, PolicyStatus status) {
        TrafficPolicy policy = policyStore.findPolicy(resource)
                .orElseThrow(() -> new PolicyNotFoundException(resource));

        TrafficPolicy updatedPolicy = policy.withStatus(status);
        policyStore.registerPolicy(resource, updatedPolicy);

        return updatedPolicy;
    }

    @Override
    public void removePolicy(String resource) {
        policyStore.removePolicy(resource);
    }

    @Override
    public void setDefaultPolicy(TrafficPolicy policy) {
        policyStore.setDefaultPolicy(policy);
    }
}