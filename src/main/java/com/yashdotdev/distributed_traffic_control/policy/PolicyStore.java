package com.yashdotdev.distributed_traffic_control.policy;

import java.util.List;
import java.util.Optional;

/**
 * Defines storage operations for traffic policies.
 *
 * Implementations may use in-memory or persistent storage.
 */
public interface PolicyStore {

    void registerPolicy(String resource, TrafficPolicy policy);

    Optional<TrafficPolicy> findPolicy(String resource);

    List<PolicyRegistration> findAllPolicies();

    void removePolicy(String resource);

    void setDefaultPolicy(TrafficPolicy policy);

    Optional<TrafficPolicy> findDefaultPolicy();
}