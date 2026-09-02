package com.yashdotdev.distributed_traffic_control.traffic.api;


import com.yashdotdev.distributed_traffic_control.traffic.TrafficSubjectType;
import lombok.Getter;

    @Getter
    public class TrafficSubjectRequest {

        private TrafficSubjectType type;
        private String subjectId;
    }