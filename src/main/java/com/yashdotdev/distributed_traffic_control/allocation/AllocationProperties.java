package com.yashdotdev.distributed_traffic_control.allocation;

import lombok.Getter;
import lombok.Setter;

import java.time.Duration;

@Getter
@Setter
public class AllocationProperties {

    private String nodeId;
    private Duration leaseDuration = Duration.ofSeconds(30);
}