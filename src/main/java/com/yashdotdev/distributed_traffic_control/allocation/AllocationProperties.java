package com.yashdotdev.distributed_traffic_control.allocation;

import com.yashdotdev.distributed_traffic_control.config.ValidPositiveDuration;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "allocation")
public class AllocationProperties {

    @NotBlank
    private String nodeId = "local-node";

    @ValidPositiveDuration
    private Duration leaseDuration = Duration.ofSeconds(30);

    @Min(1)
    private long leaseCapacity = 10;
}