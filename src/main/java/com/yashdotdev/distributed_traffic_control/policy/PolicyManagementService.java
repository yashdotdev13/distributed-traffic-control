package com.yashdotdev.distributed_traffic_control.policy;

import java.util.List;
import java.util.Optional;

public interface PolicyManagementService {

    TrafficPolicy registerPolicy(
            String resource,
            TrafficPolicy policy
    );

    Optional<TrafficPolicy> findPolicy(
            String resource
    );

    List<PolicyRegistration> findAllPolicies();

    TrafficPolicy updatePolicy(
            String resource,
            TrafficPolicy policy
    );

    void removePolicy(
            String resource
    );

    void setDefaultPolicy(
            TrafficPolicy policy
    );
}