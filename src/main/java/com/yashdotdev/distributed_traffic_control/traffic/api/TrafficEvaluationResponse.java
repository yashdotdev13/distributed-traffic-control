package com.yashdotdev.distributed_traffic_control.traffic.api;

import com.yashdotdev.distributed_traffic_control.traffic.TrafficDecisionStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TrafficEvaluationResponse {

    private TrafficDecisionStatus status;
    private String reason;
    private long remainingCapacity;
}