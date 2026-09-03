package com.yashdotdev.distributed_traffic_control.policy.api;

import com.yashdotdev.distributed_traffic_control.policy.PolicyStatus;
import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicyType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;

@Getter
public class PolicyCreateRequest {

    @NotBlank
    private String policyId;

    @NotBlank
    private String name;

    @NotNull
    private TrafficPolicyType type;

    @NotNull
    private PolicyStatus status;

    @Min(1)
    private long capacity;

    @Min(1)
    private long refillRate;

    private Duration windowDuration;

    @NotNull
    private Instant createdAt;

    @NotBlank
    private String resource;
}