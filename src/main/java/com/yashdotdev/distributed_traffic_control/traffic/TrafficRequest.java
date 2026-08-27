package com.yashdotdev.distributed_traffic_control.traffic;


import lombok.Getter;

import java.time.Instant;
import java.util.Objects;

@Getter
public class TrafficRequest {

    private final String requestId;

    private final TrafficSubject subject;

    private final String resource;

    private final Instant requestedAt;

    public TrafficRequest(
            String requestId,
            TrafficSubject subject,
            String resource,
            Instant requestedAt
    ){
        this.requestId = Objects.requireNonNull(requestId,"requestId must not be null");
        this.subject = Objects.requireNonNull(subject,"subject must not be null");
        this.resource = Objects.requireNonNull(resource,"resource must not be null");
        this.requestedAt = Objects.requireNonNull(requestedAt,"requestedAt must not be null");
    }
}
