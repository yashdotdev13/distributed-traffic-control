package com.yashdotdev.distributed_traffic_control.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

@ConfigurationProperties(prefix = "traffic-control.redis")
@Validated
public class RedisKeyProperties {

    @NotBlank(message = "Redis key prefix must not be blank")
    @ValidRedisKeyNamespace
    private String keyNamespace = "traffic-control";

    public RedisKeyProperties() {
    }

    public RedisKeyProperties(String keyNamespace) {
        this.keyNamespace = keyNamespace;
    }

    public String getKeyNamespace() {
        return keyNamespace;
    }

    public void setKeyNamespace(String keyNamespace) {
        this.keyNamespace = keyNamespace;
    }
}
