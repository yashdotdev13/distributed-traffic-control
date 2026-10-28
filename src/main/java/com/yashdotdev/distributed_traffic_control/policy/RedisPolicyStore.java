package com.yashdotdev.distributed_traffic_control.policy;

import com.yashdotdev.distributed_traffic_control.traffic.TrafficRequest;
import com.yashdotdev.distributed_traffic_control.traffic.TrafficSubject;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class RedisPolicyStore implements PolicyStore, PolicyProvider {

    private static final String RESOURCE_PREFIX =
            "traffic-control:policies:resource:";

    private static final String SUBJECT_PREFIX =
            "traffic-control:policies:subject:";

    private static final String DEFAULT_POLICY_KEY =
            "traffic-control:policies:default";

    private static final String RESOURCE_INDEX_KEY =
            "traffic-control:policies:resources";

    private final StringRedisTemplate redisTemplate;

    public RedisPolicyStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = Objects.requireNonNull(
                redisTemplate,
                "redisTemplate must not be null"
        );
    }

    @Override
    public void registerPolicy(String resource, TrafficPolicy policy) {
        validateResource(resource);
        Objects.requireNonNull(policy, "policy must not be null");

        redisTemplate.opsForValue().set(resourceKey(resource), serialize(policy));
        redisTemplate.opsForSet().add(RESOURCE_INDEX_KEY, resource);
    }

    @Override
    public Optional<TrafficPolicy> findPolicy(String resource) {
        validateResource(resource);
        return readPolicy(resourceKey(resource));
    }

    @Override
    public List<PolicyRegistration> findAllPolicies() {
        Set<String> resources =
                redisTemplate.opsForSet().members(RESOURCE_INDEX_KEY);

        if (resources == null || resources.isEmpty()) {
            return List.of();
        }

        return resources.stream()
                .map(resource -> findPolicy(resource)
                        .map(policy -> new PolicyRegistration(resource, policy)))
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public void removePolicy(String resource) {
        validateResource(resource);

        redisTemplate.delete(resourceKey(resource));
        redisTemplate.opsForSet().remove(RESOURCE_INDEX_KEY, resource);
    }

    @Override
    public void setDefaultPolicy(TrafficPolicy policy) {
        Objects.requireNonNull(policy, "policy must not be null");

        redisTemplate.opsForValue().set(
                DEFAULT_POLICY_KEY,
                serialize(policy)
        );
    }

    /**
     * Initializes the default policy only if Redis does not already contain one.
     * Existing configuration is therefore preserved across application restarts.
     */
    public void initializeDefaultPolicy(TrafficPolicy policy) {
        Objects.requireNonNull(policy, "policy must not be null");

        redisTemplate.opsForValue().setIfAbsent(
                DEFAULT_POLICY_KEY,
                serialize(policy)
        );
    }

    @Override
    public Optional<TrafficPolicy> findDefaultPolicy() {
        return readPolicy(DEFAULT_POLICY_KEY);
    }

    public void registerPolicy(
            TrafficSubject subject,
            String resource,
            TrafficPolicy policy) {

        Objects.requireNonNull(subject, "subject must not be null");
        validateResource(resource);
        Objects.requireNonNull(policy, "policy must not be null");

        redisTemplate.opsForValue().set(
                subjectKey(subject, resource),
                serialize(policy)
        );
    }

    public Optional<TrafficPolicy> findPolicy(
            TrafficSubject subject,
            String resource) {

        Objects.requireNonNull(subject, "subject must not be null");
        validateResource(resource);

        return readPolicy(subjectKey(subject, resource));
    }

    public void removePolicy(TrafficSubject subject, String resource) {
        Objects.requireNonNull(subject, "subject must not be null");
        validateResource(resource);

        redisTemplate.delete(subjectKey(subject, resource));
    }

    @Override
    public Optional<TrafficPolicy> findPolicy(TrafficRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        Optional<TrafficPolicy> subjectPolicy =
                findPolicy(request.getSubject(), request.getResource());

        if (subjectPolicy.isPresent()) {
            return subjectPolicy;
        }

        Optional<TrafficPolicy> resourcePolicy =
                findPolicy(request.getResource());

        if (resourcePolicy.isPresent()) {
            return resourcePolicy;
        }

        return findDefaultPolicy();
    }

    private Optional<TrafficPolicy> readPolicy(String key) {
        String value = redisTemplate.opsForValue().get(key);

        return value == null
                ? Optional.empty()
                : Optional.of(deserialize(value));
    }

    private String resourceKey(String resource) {
        return RESOURCE_PREFIX + encode(resource);
    }

    private String subjectKey(TrafficSubject subject, String resource) {
        return SUBJECT_PREFIX
                + encode(subject.getType().name())
                + ":"
                + encode(subject.getSubjectId())
                + ":"
                + encode(resource);
    }

    private String encode(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String serialize(TrafficPolicy policy) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();

            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeUTF(policy.getPolicyId());
                out.writeUTF(policy.getName());
                out.writeUTF(policy.getType().name());
                out.writeUTF(policy.getStatus().name());
                out.writeLong(policy.getCapacity());
                out.writeLong(policy.getRefillRate());

                Duration window = policy.getWindowDuration();
                out.writeBoolean(window != null);

                if (window != null) {
                    out.writeLong(window.getSeconds());
                    out.writeInt(window.getNano());
                }

                Instant createdAt = policy.getCreatedAt();
                out.writeLong(createdAt.getEpochSecond());
                out.writeInt(createdAt.getNano());
            }

            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not serialize traffic policy",
                    exception
            );
        }
    }

    private TrafficPolicy deserialize(String value) {
        try {
            byte[] bytes = Base64.getDecoder().decode(value);

            try (DataInputStream in =
                         new DataInputStream(new ByteArrayInputStream(bytes))) {

                String policyId = in.readUTF();
                String name = in.readUTF();
                TrafficPolicyType type =
                        TrafficPolicyType.valueOf(in.readUTF());
                PolicyStatus status =
                        PolicyStatus.valueOf(in.readUTF());
                long capacity = in.readLong();
                long refillRate = in.readLong();

                Duration window = null;

                if (in.readBoolean()) {
                    window = Duration.ofSeconds(
                            in.readLong(),
                            in.readInt()
                    );
                }

                Instant createdAt = Instant.ofEpochSecond(
                        in.readLong(),
                        in.readInt()
                );

                if (in.available() != 0) {
                    throw new IllegalStateException(
                            "Unexpected trailing data in stored traffic policy"
                    );
                }

                return new TrafficPolicy(
                        policyId,
                        name,
                        type,
                        status,
                        capacity,
                        refillRate,
                        window,
                        createdAt
                );
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Could not deserialize stored traffic policy",
                    exception
            );
        }
    }

    private void validateResource(String resource) {
        if (resource == null || resource.isBlank()) {
            throw new IllegalArgumentException(
                    "resource must not be null or blank"
            );
        }
    }
}