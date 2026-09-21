package com.yashdotdev.distributed_traffic_control.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RedisConnectionConfigurationValidator implements InitializingBean {

    private final DataRedisProperties redisProperties;

    public RedisConnectionConfigurationValidator(DataRedisProperties redisProperties) {
        this.redisProperties = redisProperties;
    }

    @Override
    public void afterPropertiesSet() {
        String host = redisProperties.getHost();
        int port = redisProperties.getPort();

        if (!StringUtils.hasText(host)) {
            throw new IllegalStateException("Redis host must not be blank");
        }

        if (port < 1 || port > 65535) {
            throw new IllegalStateException(
                    "Redis port must be between 1 and 65535: " + port
            );
        }
    }
}