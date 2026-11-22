package com.yashdotdev.distributed_traffic_control.config;

import com.yashdotdev.distributed_traffic_control.lease.GlobalCapacityKey;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RedisKeyPropertiesValidationTest {

    private Validator validator;
    private RedisKeyNamespaceValidator namespaceValidator;

    @BeforeEach
    void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
        namespaceValidator = new RedisKeyNamespaceValidator();
    }

    @Test
    void shouldAcceptDefaultKeyProperties() {
        RedisKeyProperties properties = new RedisKeyProperties();
        Set<ConstraintViolation<RedisKeyProperties>> violations = validator.validate(properties);
        assertTrue(violations.isEmpty(), "Default properties should be valid");
        assertEquals("traffic-control", properties.getKeyNamespace());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "traffic-control",
            "dev:traffic-control",
            "staging:traffic-control",
            "prod:traffic-control",
            "custom-ns",
            "app.env.v1"
    })
    void shouldAcceptValidKeyNamespaces(String namespace) {
        RedisKeyProperties properties = new RedisKeyProperties(namespace);
        Set<ConstraintViolation<RedisKeyProperties>> violations = validator.validate(properties);
        assertTrue(violations.isEmpty(), "Namespace should be valid: " + namespace);
        assertTrue(namespaceValidator.isValid(namespace, null));
    }

    @Test
    void shouldRejectNullKeyNamespace() {
        RedisKeyProperties properties = new RedisKeyProperties(null);
        Set<ConstraintViolation<RedisKeyProperties>> violations = validator.validate(properties);
        assertFalse(violations.isEmpty(), "Null namespace should cause validation failure");
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("keyNamespace")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t", "\n"})
    void shouldRejectBlankKeyNamespace(String blankNamespace) {
        RedisKeyProperties properties = new RedisKeyProperties(blankNamespace);
        Set<ConstraintViolation<RedisKeyProperties>> violations = validator.validate(properties);
        assertFalse(violations.isEmpty(), "Blank namespace should cause validation failure");
    }

    @ParameterizedTest
    @ValueSource(strings = {"traffic-control:", "dev:", "a:b:c:", ":"})
    void shouldRejectNamespaceEndingWithColon(String colonNamespace) {
        RedisKeyProperties properties = new RedisKeyProperties(colonNamespace);
        Set<ConstraintViolation<RedisKeyProperties>> violations = validator.validate(properties);
        assertFalse(violations.isEmpty(), "Namespace ending with colon should fail validation");
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("must not end with a colon")));
        assertFalse(namespaceValidator.isValid(colonNamespace, null));
    }

    @Test
    void shouldHandleNullInConstraintValidatorGracefully() {
        // Bean validation standard: validator returns true for null, @NotBlank catches null
        assertTrue(namespaceValidator.isValid(null, null));
    }

    @Test
    void shouldBuildExpectedKeysWithDefaultNamespace() {
        RedisKeyBuilder builder = new RedisKeyBuilder(new RedisKeyProperties());
        GlobalCapacityKey capacityKey = new GlobalCapacityKey("policy-1", "/api/orders");

        assertEquals("traffic-control:quota:policy-1:/api/orders", builder.globalCapacityKey(capacityKey));
        assertEquals("traffic-control:quota:policy-1:/api/orders:leases", builder.leaseRegistryKey(capacityKey));
        assertEquals("traffic-control:lease:lease-123", builder.leaseKey("lease-123"));
        assertEquals("traffic-control:token-bucket:policy-1:/api/orders", builder.tokenBucketKey(capacityKey));
    }

    @Test
    void shouldBuildExpectedKeysWithCustomNamespace() {
        RedisKeyBuilder builder = new RedisKeyBuilder("staging:traffic-control");
        GlobalCapacityKey capacityKey = new GlobalCapacityKey("policy-1", "/api/orders");

        assertEquals("staging:traffic-control:quota:policy-1:/api/orders", builder.globalCapacityKey(capacityKey));
        assertEquals("staging:traffic-control:quota:policy-1:/api/orders:leases", builder.leaseRegistryKey(capacityKey));
        assertEquals("staging:traffic-control:lease:lease-123", builder.leaseKey("lease-123"));
        assertEquals("staging:traffic-control:token-bucket:policy-1:/api/orders", builder.tokenBucketKey(capacityKey));
    }

    @Test
    void shouldRejectInvalidArgumentsInKeyBuilder() {
        assertThrows(IllegalArgumentException.class, () -> new RedisKeyBuilder((String) null));
        assertThrows(IllegalArgumentException.class, () -> new RedisKeyBuilder(""));
        assertThrows(IllegalArgumentException.class, () -> new RedisKeyBuilder("   "));
        assertThrows(IllegalArgumentException.class, () -> new RedisKeyBuilder("invalid:"));

        assertThrows(IllegalArgumentException.class, () -> new RedisKeyBuilder((RedisKeyProperties) null));
        assertThrows(IllegalArgumentException.class, () -> new RedisKeyBuilder(new RedisKeyProperties(null)));
        assertThrows(IllegalArgumentException.class, () -> new RedisKeyBuilder(new RedisKeyProperties("")));
        assertThrows(IllegalArgumentException.class, () -> new RedisKeyBuilder(new RedisKeyProperties("ends-with-colon:")));

        RedisKeyBuilder builder = new RedisKeyBuilder("test-ns");
        assertThrows(NullPointerException.class, () -> builder.globalCapacityKey(null));
        assertThrows(NullPointerException.class, () -> builder.leaseRegistryKey(null));
        assertThrows(NullPointerException.class, () -> builder.tokenBucketKey(null));
        assertThrows(IllegalArgumentException.class, () -> builder.leaseKey(null));
        assertThrows(IllegalArgumentException.class, () -> builder.leaseKey(""));
        assertThrows(IllegalArgumentException.class, () -> builder.leaseKey("   "));
    }
}
