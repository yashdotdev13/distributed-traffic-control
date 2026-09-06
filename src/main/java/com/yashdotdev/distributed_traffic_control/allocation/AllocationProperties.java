package com.yashdotdev.distributed_traffic_control.allocation;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "allocation")
public class AllocationProperties {

    private String nodeId = "local-node";
    private Duration leaseDuration = Duration.ofSeconds(30);
}