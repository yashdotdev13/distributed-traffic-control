package com.yashdotdev.distributed_traffic_control.lease;


public record GlobalCapacityKey(
        String policyId,
        String resource
) {

    public GlobalCapacityKey {
        if (policyId == null || policyId.isBlank()) {
            throw new IllegalArgumentException(
                    "policyId must not be null or blank"
            );
        }

        if (resource == null || resource.isBlank()) {
            throw new IllegalArgumentException(
                    "resource must not be null or blank"
            );
        }
    }
}