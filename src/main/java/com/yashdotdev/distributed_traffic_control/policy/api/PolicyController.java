package com.yashdotdev.distributed_traffic_control.policy.api;

import com.yashdotdev.distributed_traffic_control.policy.PolicyManagementService;
import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicy;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
                new TrafficPolicy(
                        request.getPolicyId(),
                        request.getName(),
                        request.getType(),
                        request.getStatus(),
                        request.getCapacity(),
                        request.getRefillRate(),
                        request.getWindowDuration(),
                        request.getCreatedAt()
                );

        TrafficPolicy registeredPolicy =
                policyManagementService.registerPolicy(
                        request.getResource(),
                        policy
                );

        PolicyResponse response =
                new PolicyResponse(
                        registeredPolicy.getPolicyId(),
                        registeredPolicy.getName(),
                        registeredPolicy.getType(),
                        registeredPolicy.getStatus(),
                        registeredPolicy.getCapacity(),
                        registeredPolicy.getRefillRate(),
                        registeredPolicy.getWindowDuration(),
                        registeredPolicy.getCreatedAt(),
                        request.getResource()
                );

        return ResponseEntity.ok(response);
    }

    @DeleteMapping
    public ResponseEntity<Void> removePolicy(
            @RequestParam String resource
    ) {

        policyManagementService.removePolicy(
                resource
        );

        return ResponseEntity.noContent().build();
    }
}