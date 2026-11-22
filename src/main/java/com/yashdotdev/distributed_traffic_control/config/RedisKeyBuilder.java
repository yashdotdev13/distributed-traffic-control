package com.yashdotdev.distributed_traffic_control.config;

import com.yashdotdev.distributed_traffic_control.lease.GlobalCapacityKey;
import com.yashdotdev.distributed_traffic_control.traffic.TrafficSubject;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

public class RedisKeyBuilder {

    private final String namespace;

    public RedisKeyBuilder(String namespace) {
        if (namespace == null || namespace.isBlank() || namespace.endsWith(":")) {
            throw new IllegalArgumentException(
                    "Redis key namespace must not be null or blank and must not end with ':'"
            );
        }
        this.namespace = namespace;
    }

    public RedisKeyBuilder(RedisKeyProperties properties) {
        if (properties == null) {
            throw new IllegalArgumentException("properties must not be null");
        }
        String ns = properties.getKeyNamespace();
        if (ns == null || ns.isBlank() || ns.endsWith(":")) {
            throw new IllegalArgumentException(
                    "Redis key namespace must not be null or blank and must not end with ':'"
            );
        }
        this.namespace = ns;
    }

    public String getNamespace() {
        return namespace;
    }

    public String defaultPolicyKey() {
        return namespace + ":policies:default";
    }

    public String resourcePolicyKey(String resource) {
        return namespace + ":policies:resource:" + encode(resource);
    }

    public String subjectPolicyKey(
            TrafficSubject subject,
            String resource) {

        Objects.requireNonNull(subject, "subject must not be null");

        return namespace + ":policies:subject:"
                + encode(subject.getType().name())
                + ":"
                + encode(subject.getSubjectId())
                + ":"
                + encode(resource);
    }

    public String resourceIndexKey() {
        return namespace + ":policies:resources";
    }

    public String globalCapacityKey(GlobalCapacityKey capacityKey) {
        Objects.requireNonNull(capacityKey, "capacityKey must not be null");

        return namespace + ":quota:"
                + capacityKey.policyId()
                + ":"
                + capacityKey.resource();
    }

    public String leaseRegistryKey(GlobalCapacityKey capacityKey) {
        Objects.requireNonNull(capacityKey, "capacityKey must not be null");
        return globalCapacityKey(capacityKey) + ":leases";
    }

    public String leaseKey(String leaseId) {
        if (leaseId == null || leaseId.isBlank()) {
            throw new IllegalArgumentException(
                    "leaseId must not be null or blank");
        }

        return namespace + ":lease:" + leaseId;
    }

    public String tokenBucketKey(GlobalCapacityKey capacityKey) {
        Objects.requireNonNull(capacityKey, "capacityKey must not be null");

        return namespace + ":token-bucket:"
                + capacityKey.policyId()
                + ":"
                + capacityKey.resource();
    }

    private String encode(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
