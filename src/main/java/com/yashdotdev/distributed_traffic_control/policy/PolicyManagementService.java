package com.yashdotdev.distributed_traffic_control.policy;

public interface  PolicyManagementService {


    TrafficPolicy registerPolicy(String resource, TrafficPolicy policy);

    void removePolicy(String resource);

    void setDefaultPolicy(TrafficPolicy policy);
}
