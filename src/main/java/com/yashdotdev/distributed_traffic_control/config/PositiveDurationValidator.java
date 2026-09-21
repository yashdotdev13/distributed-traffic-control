package com.yashdotdev.distributed_traffic_control.config;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.Duration;

public class PositiveDurationValidator
        implements ConstraintValidator<ValidPositiveDuration, Duration> {

    @Override
    public boolean isValid(Duration value, ConstraintValidatorContext context) {
        return value != null && !value.isZero() && !value.isNegative();
    }
}