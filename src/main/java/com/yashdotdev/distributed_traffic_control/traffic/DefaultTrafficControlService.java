package com.yashdotdev.distributed_traffic_control.traffic;


import lombok.RequiredArgsConstructor;

import java.util.Objects;

@RequiredArgsConstructor
public class DefaultTrafficControlService implements TrafficControlService{

    private final TrafficDecisionEngine trafficDecisionEngine;


    @Override
    public TrafficDecision evaluate(TrafficRequest request) {


        Objects.requireNonNull(request,"request must not be null");

        return trafficDecisionEngine.evaluate(request);
    }
}
