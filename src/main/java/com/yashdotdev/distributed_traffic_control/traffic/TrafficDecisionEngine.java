package com.yashdotdev.distributed_traffic_control.traffic;

import com.yashdotdev.distributed_traffic_control.allocation.CapacityAllocator;
import com.yashdotdev.distributed_traffic_control.lease.LeaseConsumptionResult;
import com.yashdotdev.distributed_traffic_control.lease.QuotaLease;
import com.yashdotdev.distributed_traffic_control.monitoring.TrafficControlMetrics;
import com.yashdotdev.distributed_traffic_control.policy.PolicyProvider;
import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicy;
import com.yashdotdev.distributed_traffic_control.quota.QuotaConsumptionResult;
import com.yashdotdev.distributed_traffic_control.quota.QuotaCoordinator;
import com.yashdotdev.distributed_traffic_control.quota.QuotaKey;
import io.micrometer.core.instrument.Timer;

import java.util.Optional;

public class TrafficDecisionEngine {

    private final PolicyProvider policyProvider;
    private final QuotaCoordinator quotaCoordinator;
    private final CapacityAllocator capacityAllocator;
    private final TrafficControlMetrics metrics;

    public TrafficDecisionEngine(PolicyProvider policyProvider, QuotaCoordinator quotaCoordinator, CapacityAllocator capacityAllocator) {
        this(policyProvider, quotaCoordinator, capacityAllocator, null);
    }

    public TrafficDecisionEngine(PolicyProvider policyProvider, QuotaCoordinator quotaCoordinator, CapacityAllocator capacityAllocator, TrafficControlMetrics metrics) {
        this.policyProvider = policyProvider;
        this.quotaCoordinator = quotaCoordinator;
        this.capacityAllocator = capacityAllocator;
        this.metrics = metrics;
    }

    public TrafficDecision evaluate(TrafficRequest request) {

        Timer.Sample timer = null;
        if (metrics != null) {
            timer = metrics.startDecisionTimer();
        }

        try {
            Optional<TrafficPolicy> policy = policyProvider.findPolicy(request);
            if (policy.isEmpty()) {
                recordRejected();
                return new TrafficDecision(TrafficDecisionStatus.REJECTED, "No traffic policy found", 0);
            }

            TrafficPolicy trafficPolicy = policy.get();
            if (!trafficPolicy.isActive()) {
                recordRejected();
                return new TrafficDecision(TrafficDecisionStatus.REJECTED, "Traffic policy is inactive", 0);
            }

            QuotaKey quotaKey = new QuotaKey(trafficPolicy.getPolicyId(), request.getSubject(), request.getResource());
            QuotaConsumptionResult consumptionResult = quotaCoordinator.tryConsume(quotaKey, trafficPolicy);
            if (consumptionResult.isConsumed()) {
                recordAllowed();

                return new TrafficDecision(TrafficDecisionStatus.ALLOWED, "Request allowed", consumptionResult.getRemainingCapacity());
            }
            recordQuotaExhausted();
            recordLeaseAllocationAttempt();
            Optional<QuotaLease> lease = capacityAllocator.allocate(trafficPolicy, quotaKey);

            if (lease.isEmpty()) {
                recordLeaseAllocationFailure();
                recordRejected();

                return new TrafficDecision(TrafficDecisionStatus.REJECTED, "Traffic quota exhausted", consumptionResult.getRemainingCapacity());
            }
            recordLeaseAllocationSuccess();
            LeaseConsumptionResult leaseConsumptionResult = capacityAllocator.tryConsume(lease.get(), request.getRequestedAt());
            if (!leaseConsumptionResult.isConsumed()) {
                recordRejected();

                return new TrafficDecision(TrafficDecisionStatus.REJECTED, "Traffic quota exhausted", leaseConsumptionResult.getRemainingCapacity());
            }
            recordAllowed();
            return new TrafficDecision(TrafficDecisionStatus.ALLOWED, "Request allowed", leaseConsumptionResult.getRemainingCapacity());

        } finally {
            if (timer != null) {
                metrics.recordDecisionDuration(timer);
            }
        }
    }
    private void recordAllowed() {
        if (metrics != null) {
            metrics.recordAllowed();
        }
    }
    private void recordRejected() {
        if (metrics != null) {
            metrics.recordRejected();
        }
    }
    private void recordQuotaExhausted() {
        if (metrics != null) {
            metrics.recordQuotaExhausted();
        }
    }
    private void recordLeaseAllocationAttempt() {
        if (metrics != null) {
            metrics.recordLeaseAllocationAttempt();
        }
    }
    private void recordLeaseAllocationSuccess() {
        if (metrics != null) {
            metrics.recordLeaseAllocationSuccess();
        }
    }
    private void recordLeaseAllocationFailure() {
        if (metrics != null) {
            metrics.recordLeaseAllocationFailure();
        }
    }
}