package com.yashdotdev.distributed_traffic_control.policy;

import com.yashdotdev.distributed_traffic_control.traffic.TrafficRequest;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryPolicyProvider implements PolicyProvider {

    private final Map<String, TrafficPolicy> policiesByResource =
            new ConcurrentHashMap<>();

    private volatile TrafficPolicy defaultPolicy;

    public InMemoryPolicyProvider() {
    }

    public InMemoryPolicyProvider(
            TrafficPolicy defaultPolicy
    ) {
        this.defaultPolicy = Objects.requireNonNull(
                defaultPolicy,
                "defaultPolicy must not be null"
        );
    }
    public void registerPolicy(
            String resource,
            TrafficPolicy policy
    ) {
        if (resource == null || resource.isBlank()) {
            throw new IllegalArgumentException(
                    "resource must not be null or blank"
            );
        }

        Objects.requireNonNull(
                policy,
                "policy must not be null"
        );

        policiesByResource.put(
                resource,
                policy
        );
    }

    public void removePolicy(
            String resource
    ) {
        if (resource == null || resource.isBlank()) {
            throw new IllegalArgumentException(
                    "resource must not be null or blank"
            );
        }

        policiesByResource.remove(resource);
    }

    public void setDefaultPolicy(
            TrafficPolicy policy
    ) {
        this.defaultPolicy = Objects.requireNonNull(
                policy,
                "policy must not be null"
        );
    }

    public Optional<TrafficPolicy> findPolicy(
            TrafficRequest request
    ) {
        Objects.requireNonNull(
                request,
                "request must not be null"
        );

        TrafficPolicy resourcePolicy =
                policiesByResource.get(
                        request.getResource()
                );

        if (resourcePolicy != null) {
            return Optional.of(resourcePolicy);
        }

        return Optional.ofNullable(defaultPolicy);
    }
}