package com.yashdotdev.distributed_traffic_control.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisConnectionConfigurationValidatorTest {

    @Test
    void shouldAcceptValidConfiguration() {
        DataRedisProperties properties = new DataRedisProperties();
        properties.setHost("localhost");
        properties.setPort(6379);

        RedisConnectionConfigurationValidator validator =
                new RedisConnectionConfigurationValidator(properties);

        assertThatCode(validator::afterPropertiesSet)
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectBlankHost() {
        DataRedisProperties properties = new DataRedisProperties();
        properties.setHost("   ");
        properties.setPort(6379);

        RedisConnectionConfigurationValidator validator =
                new RedisConnectionConfigurationValidator(properties);

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Redis host must not be blank");
    }

    @Test
    void shouldRejectInvalidLowPort() {
        DataRedisProperties properties = new DataRedisProperties();
        properties.setHost("localhost");
        properties.setPort(0);

        RedisConnectionConfigurationValidator validator =
                new RedisConnectionConfigurationValidator(properties);

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Redis port must be between 1 and 65535: 0");
    }

    @Test
    void shouldRejectInvalidHighPort() {
        DataRedisProperties properties = new DataRedisProperties();
        properties.setHost("localhost");
        properties.setPort(65536);

        RedisConnectionConfigurationValidator validator =
                new RedisConnectionConfigurationValidator(properties);

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Redis port must be between 1 and 65535: 65536");
    }
}