package com.yashdotdev.distributed_traffic_control.traffic;

public interface  TrafficControlService {

    TrafficDecision evaluate(TrafficRequest request);
}
