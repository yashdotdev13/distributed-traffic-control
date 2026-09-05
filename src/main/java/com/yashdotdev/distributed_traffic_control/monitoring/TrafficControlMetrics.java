package com.yashdotdev.distributed_traffic_control.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;


public class TrafficControlMetrics {

    private final MeterRegistry meterRegistry;
    private final Timer decisionTimer;
    private final Counter allowedCounter;
    private final Counter rejectedCounter;
    private final Counter quotaExhaustedCounter;
    private final Counter leaseAllocationAttemptCounter;
    private final Counter leaseAllocationSuccessCounter;
    private final Counter leaseAllocationFailureCounter;

    public TrafficControlMetrics(MeterRegistry meterRegistry) {
        if (meterRegistry == null) {
            throw new IllegalArgumentException(
                    "meterRegistry must not be null"
            );
        }

        this.meterRegistry = meterRegistry;
        this.decisionTimer =
                Timer.builder("traffic_control_decision_duration")
                        .description(
                                "Time spent evaluating traffic-control decisions"
                        )
                        .publishPercentiles(
                                0.50,
                                0.95,
                                0.99
                        )
                        .register(meterRegistry);
        this.allowedCounter =
                Counter.builder("traffic_control_decisions_total")
                        .description(
                                "Traffic-control decisions"
                        )
                        .tag("status", "allowed")
                        .register(meterRegistry);
        this.rejectedCounter =
                Counter.builder("traffic_control_decisions_total")
                        .description(
                                "Traffic-control decisions"
                        )
                        .tag("status", "rejected")
                        .register(meterRegistry);
        this.quotaExhaustedCounter =
                Counter.builder("traffic_control_quota_exhausted_total")
                        .description(
                                "Number of times local quota was exhausted"
                        )
                        .register(meterRegistry);
        this.leaseAllocationAttemptCounter =
                Counter.builder("traffic_control_lease_allocations_total")
                        .description(
                                "Lease allocation attempts"
                        )
                        .tag("result", "attempt")
                        .register(meterRegistry);
        this.leaseAllocationSuccessCounter =
                Counter.builder("traffic_control_lease_allocations_total")
                        .description(
                                "Lease allocation results"
                        )
                        .tag("result", "success")
                        .register(meterRegistry);
        this.leaseAllocationFailureCounter =
                Counter.builder("traffic_control_lease_allocations_total")
                        .description(
                                "Lease allocation results"
                        )
                        .tag("result", "failure")
                        .register(meterRegistry);
    }
    public void recordAllowed() {
        allowedCounter.increment();
    }

    public void recordRejected() {
        rejectedCounter.increment();
    }

    public void recordQuotaExhausted() {
        quotaExhaustedCounter.increment();
    }

    public void recordLeaseAllocationAttempt() {
        leaseAllocationAttemptCounter.increment();
    }

    public void recordLeaseAllocationSuccess() {
        leaseAllocationSuccessCounter.increment();
    }

    public void recordLeaseAllocationFailure() {
        leaseAllocationFailureCounter.increment();
    }

    public Timer.Sample startDecisionTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordDecisionDuration(
            Timer.Sample sample
    ) {
        sample.stop(decisionTimer);
    }
}