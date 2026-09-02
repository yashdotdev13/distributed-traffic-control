package com.yashdotdev.distributed_traffic_control.traffic.api;


import lombok.Getter;

import java.time.Instant;

@Getter
public class TrafficEvaluationRequest {

    private String requestId;
    private TrafficSubjectRequest subject;
    private String resource;
    private Instant requestedAt;
}
