package com.yashdotdev.distributed_traffic_control.quota;


import com.yashdotdev.distributed_traffic_control.traffic.TrafficSubject;
import lombok.Getter;

import java.util.Objects;

@Getter
public class QuotaKey {

    private final String policyId;
    private final TrafficSubject subject;
    private final String resources;

    public QuotaKey(String policyId, TrafficSubject subject, String resource){

        this.policyId = Objects.requireNonNull(policyId,"PolicyId must not be null");
        this.subject = Objects.requireNonNull(subject,"Subject must not be null");
        this.resources = Objects.requireNonNull(resource,"Resource must not be null");
    }
}
