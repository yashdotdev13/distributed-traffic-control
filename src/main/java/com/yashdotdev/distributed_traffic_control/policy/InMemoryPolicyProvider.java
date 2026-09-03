package com.yashdotdev.distributed_traffic_control.policy;

import com.yashdotdev.distributed_traffic_control.traffic.TrafficRequest;
import com.yashdotdev.distributed_traffic_control.traffic.TrafficSubject;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryPolicyProvider implements PolicyProvider {

    private final Map<String, TrafficPolicy> policiesByResource =
            new ConcurrentHashMap<>();

    private final Map<PolicyMatchKey, TrafficPolicy>
            policiesBySubjectAndResource =
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
        validateResource(resource);

        Objects.requireNonNull(
                policy,
                "policy must not be null"
        );

        policiesByResource.put(
                resource,
                policy
        );
    }

    public void registerPolicy(
            TrafficSubject subject,
            String resource,
            TrafficPolicy policy
    ) {
        Objects.requireNonNull(
                subject,
                "subject must not be null"
        );

        validateResource(resource);

        Objects.requireNonNull(
                policy,
                "policy must not be null"
        );

        policiesBySubjectAndResource.put(
                new PolicyMatchKey(
                        subject,
                        resource
                ),
                policy
        );
    }

    public Optional<TrafficPolicy> findPolicy(
            String resource
    ) {
        validateResource(resource);

        return Optional.ofNullable(
                policiesByResource.get(resource)
        );
    }

    public List<Map.Entry<String, TrafficPolicy>> findAllPolicies() {

        return List.copyOf(
                policiesByResource.entrySet()
        );
    }

    public void removePolicy(
            String resource
    ) {
        validateResource(resource);

        policiesByResource.remove(resource);
    }

    public void removePolicy(
            TrafficSubject subject,
            String resource
    ) {
        Objects.requireNonNull(
                subject,
                "subject must not be null"
        );

        validateResource(resource);

        policiesBySubjectAndResource.remove(
                new PolicyMatchKey(
                        subject,
                        resource
                )
        );
    }

    public void setDefaultPolicy(
            TrafficPolicy policy
    ) {
        this.defaultPolicy =
                Objects.requireNonNull(
                        policy,
                        "policy must not be null"
                );
    }

    @Override
    public Optional<TrafficPolicy> findPolicy(
            TrafficRequest request
    ) {
        Objects.requireNonNull(
                request,
                "request must not be null"
        );

        TrafficPolicy subjectPolicy =
                policiesBySubjectAndResource.get(
                        new PolicyMatchKey(
                                request.getSubject(),
                                request.getResource()
                        )
                );

        if (subjectPolicy != null) {
            return Optional.of(subjectPolicy);
        }

        TrafficPolicy resourcePolicy =
                policiesByResource.get(
                        request.getResource()
                );

        if (resourcePolicy != null) {
            return Optional.of(resourcePolicy);
        }

        return Optional.ofNullable(
                defaultPolicy
        );
    }

    private void validateResource(
            String resource
    ) {
        if (resource == null || resource.isBlank()) {
            throw new IllegalArgumentException(
                    "resource must not be null or blank"
            );
        }
    }
}