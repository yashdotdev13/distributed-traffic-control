package com.yashdotdev.distributed_traffic_control.traffic;

import com.yashdotdev.distributed_traffic_control.allocation.CapacityAllocator;
import com.yashdotdev.distributed_traffic_control.policy.PolicyProvider;
import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicy;
import com.yashdotdev.distributed_traffic_control.quota.QuotaConsumptionResult;
import com.yashdotdev.distributed_traffic_control.quota.QuotaCoordinator;
import com.yashdotdev.distributed_traffic_control.quota.QuotaKey;
import com.yashdotdev.distributed_traffic_control.lease.QuotaLease;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

@RequiredArgsConstructor
public class TrafficDecisionEngine {

    private final PolicyProvider policyProvider;
    private final QuotaCoordinator quotaCoordinator;
    private final CapacityAllocator capacityAllocator;

    public TrafficDecision evaluate(TrafficRequest request) {

        Optional<TrafficPolicy> policy =
                policyProvider.findPolicy(request);

        if (policy.isEmpty()) {
            return new TrafficDecision(
                    TrafficDecisionStatus.REJECTED,
                    "No traffic policy found",
                    0
            );
        }

        TrafficPolicy trafficPolicy = policy.get();

        if (!trafficPolicy.isActive()) {
            return new TrafficDecision(
                    TrafficDecisionStatus.REJECTED,
                    "Traffic policy is inactive",
                    0
            );
        }

        QuotaKey quotaKey = new QuotaKey(
                trafficPolicy.getPolicyId(),
                request.getSubject(),
                request.getResource()
        );

        QuotaConsumptionResult consumptionResult =
                quotaCoordinator.tryConsume(
                        quotaKey,
                        trafficPolicy
                );

        if (consumptionResult.isConsumed()) {
            return new TrafficDecision(
                    TrafficDecisionStatus.ALLOWED,
                    "Request allowed",
                    consumptionResult.getRemainingCapacity()
            );
        }

        Optional<QuotaLease> lease =
                capacityAllocator.allocate(
                        trafficPolicy,
                        quotaKey
                );

        if (lease.isEmpty()) {
            return new TrafficDecision(
                    TrafficDecisionStatus.REJECTED,
                    "Traffic quota exhausted",
                    consumptionResult.getRemainingCapacity()
            );
        }

        return new TrafficDecision(
                TrafficDecisionStatus.ALLOWED,
                "Request allowed",
                lease.get().getRemainingCapacity()
        );
    }
}