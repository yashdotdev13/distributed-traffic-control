package com.yashdotdev.distributed_traffic_control.policy;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class DefaultPolicyManagementService
        implements PolicyManagementService {

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
    public TrafficPolicy registerPolicy(
            String resource,
            TrafficPolicy policy
    ) {
        policyProvider.registerPolicy(
                resource,
                policy
        );

        return policy;
    }

    @Override
    public Optional<TrafficPolicy> findPolicy(
            String resource
    ) {
        return policyProvider.findPolicy(resource);
    }

    @Override
    public List<PolicyRegistration> findAllPolicies() {

        return policyProvider.findAllPolicies()
                .stream()
                .map(entry ->
                        new PolicyRegistration(
                                entry.getKey(),
                                entry.getValue()
                        )
                )
                .toList();
    }

    @Override
    public TrafficPolicy updatePolicy(
            String resource,
            TrafficPolicy policy
    ) {

        Optional<TrafficPolicy> existingPolicy =
                policyProvider.findPolicy(resource);

        if (existingPolicy.isEmpty()) {
            throw new IllegalArgumentException(
                    "No policy registered for resource: "
                            + resource
            );
        }

        policyProvider.registerPolicy(
                resource,
                policy
        );

        return policy;
    }

    @Override
    public void removePolicy(
            String resource
    ) {
        policyProvider.removePolicy(resource);
    }

    @Override
    public void setDefaultPolicy(
            TrafficPolicy policy
    ) {
        policyProvider.setDefaultPolicy(policy);
    }
}