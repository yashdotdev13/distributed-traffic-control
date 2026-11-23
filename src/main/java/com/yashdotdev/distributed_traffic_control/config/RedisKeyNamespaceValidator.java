
package com.yashdotdev.distributed_traffic_control.config;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class RedisKeyNamespaceValidator
        implements ConstraintValidator<ValidRedisKeyNamespace, String> {

    @Override
    public boolean isValid(
            String value,
            ConstraintValidatorContext context) {

        if (value == null) {
            return true;
        }

        return !value.endsWith(":");
    }
}
