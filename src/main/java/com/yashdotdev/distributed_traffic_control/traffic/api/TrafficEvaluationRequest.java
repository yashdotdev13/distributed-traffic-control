package com.yashdotdev.distributed_traffic_control.traffic.api;


import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.time.Instant;

@Getter
public class TrafficEvaluationRequest {

    @NotBlank
    private String requestId;

    @Valid
    @NotNull
    private TrafficSubjectRequest subject;

    @NotBlank
    private String resource;

    @NotNull
    private Instant requestedAt;
}