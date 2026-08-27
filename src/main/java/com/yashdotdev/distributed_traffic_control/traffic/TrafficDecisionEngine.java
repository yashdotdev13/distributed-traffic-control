package com.yashdotdev.distributed_traffic_control.traffic;


import com.yashdotdev.distributed_traffic_control.policy.PolicyProvider;
import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicy;
import com.yashdotdev.distributed_traffic_control.quota.Quota;
import com.yashdotdev.distributed_traffic_control.quota.QuotaCoordinator;
import com.yashdotdev.distributed_traffic_control.quota.QuotaKey;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

@RequiredArgsConstructor
public class TrafficDecisionEngine {

    private final PolicyProvider policyProvider;
    private final QuotaCoordinator quotaCoordinator;

    public TrafficDecision evaluate(TrafficRequest request){

        Optional<TrafficPolicy> policy =  policyProvider.findPolicy(request);

        if(policy.isEmpty()){
            return new TrafficDecision(TrafficDecisionStatus.REJECTED,
                    "No traffic policy found", 0);
        }

        TrafficPolicy trafficPolicy = policy.get();

        if(!trafficPolicy.isActive()){
            return new TrafficDecision(TrafficDecisionStatus.REJECTED,
                    "Traffic policy is inactive", 0);
        }

        QuotaKey quotaKey = new QuotaKey(
                trafficPolicy.getPolicyId(),
                request.getSubject(),
                request.getResource()
        );

        Quota quota = quotaCoordinator.acquireQuota(
                quotaKey,
                trafficPolicy.getCapacity()
        );

        if (!quota.hasAvailableCapacity()) {
            return new TrafficDecision(
                    TrafficDecisionStatus.REJECTED,
                    "Traffic quota exhausted",
                    quota.getAvailableCapacity()
            );
        }

        quota.consume();

        return new TrafficDecision(
                TrafficDecisionStatus.ALLOWED,
                "Request allowed",
                quota.getAvailableCapacity()
        );
    }
}
