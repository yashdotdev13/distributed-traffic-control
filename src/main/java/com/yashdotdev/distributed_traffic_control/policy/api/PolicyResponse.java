package com.yashdotdev.distributed_traffic_control.policy.api;

import com.yashdotdev.distributed_traffic_control.policy.PolicyStatus;
import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicyType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;

@Getter
@AllArgsConstructor
public class PolicyResponse {

    private String policyId;

    private String name;

    private TrafficPolicyType type;

    private PolicyStatus status;

    private long capacity;

    private long refillRate;

    private Duration windowDuration;

    private Instant createdAt;

    private String resource;
}