package com.yashdotdev.distributed_traffic_control.policy.api;

import com.yashdotdev.distributed_traffic_control.policy.PolicyManagementService;
import com.yashdotdev.distributed_traffic_control.policy.PolicyRegistration;
import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicy;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/policies")
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyManagementService policyManagementService;

    @PostMapping
    public ResponseEntity<PolicyResponse> createPolicy(
            @Valid @RequestBody PolicyCreateRequest request
    ) {

        TrafficPolicy policy =
                toPolicy(request);

        TrafficPolicy registeredPolicy =
                policyManagementService.registerPolicy(
                        request.getResource(),
                        policy
                );

        return ResponseEntity.ok(
                toResponse(
                        request.getResource(),
                        registeredPolicy
                )
        );
    }

    @GetMapping
    public ResponseEntity<?> getPolicies(
            @RequestParam(required = false)
            String resource
    ) {

        if (resource != null) {

            return policyManagementService
                    .findPolicy(resource)
                    .map(policy ->
                            ResponseEntity.ok(
                                    toResponse(
                                            resource,
                                            policy
                                    )
                            )
                    )
                    .orElseGet(
                            () -> ResponseEntity.notFound().build()
                    );
        }

        List<PolicyResponse> policies =
                policyManagementService
                        .findAllPolicies()
                        .stream()
                        .map(registration ->
                                toResponse(
                                        registration.resource(),
                                        registration.policy()
                                )
                        )
                        .toList();

        return ResponseEntity.ok(
                policies
        );
    }

    @PutMapping
    public ResponseEntity<PolicyResponse> updatePolicy(
            @RequestParam String resource,
            @Valid @RequestBody PolicyCreateRequest request
    ) {

        TrafficPolicy policy =
                toPolicy(request);

        TrafficPolicy updatedPolicy =
                policyManagementService.updatePolicy(
                        resource,
                        policy
                );

        return ResponseEntity.ok(
                toResponse(
                        resource,
                        updatedPolicy
                )
        );
    }

    @DeleteMapping
    public ResponseEntity<Void> removePolicy(
            @RequestParam String resource
    ) {

        policyManagementService.removePolicy(
                resource
        );

        return ResponseEntity.noContent()
                .build();
    }

    private TrafficPolicy toPolicy(
            PolicyCreateRequest request
    ) {

        return new TrafficPolicy(
                request.getPolicyId(),
                request.getName(),
                request.getType(),
                request.getStatus(),
                request.getCapacity(),
                request.getRefillRate(),
                request.getWindowDuration(),
                request.getCreatedAt()
        );
    }

    private PolicyResponse toResponse(
            String resource,
            TrafficPolicy policy
    ) {

        return new PolicyResponse(
                policy.getPolicyId(),
                policy.getName(),
                policy.getType(),
                policy.getStatus(),
                policy.getCapacity(),
                policy.getRefillRate(),
                policy.getWindowDuration(),
                policy.getCreatedAt(),
                resource
        );
    }
}