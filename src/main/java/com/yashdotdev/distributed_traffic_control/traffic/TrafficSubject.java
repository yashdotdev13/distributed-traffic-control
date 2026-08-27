package com.yashdotdev.distributed_traffic_control.traffic;

import lombok.Getter;

import java.util.Objects;

@Getter
public class TrafficSubject {

    private final String subjectId;
    private final TrafficSubjectType type;

    public TrafficSubject(String subjectId, TrafficSubjectType type) {
        this.subjectId = Objects.requireNonNull(
                subjectId,
                "subjectId must not be null"
        );

        this.type = Objects.requireNonNull(
                type,
                "type must not be null"
        );
    }
}