package com.yashdotdev.distributed_traffic_control.lease;

import com.yashdotdev.distributed_traffic_control.quota.QuotaKey;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Getter
public class QuotaLease {

    private final String leaseId;
    private final QuotaKey quotaKey;
    private final String nodeId;
    private final long allocatedCapacity;
    private long remainingCapacity;
    private final Instant issuedAt;
    private Instant expiresAt;

    public QuotaLease(
            String leaseId,
            QuotaKey quotaKey,
            String nodeId,
            long allocatedCapacity,
            Instant issuedAt,
            Instant expiresAt
    ) {
        this.leaseId = Objects.requireNonNull(
                leaseId,
                "leaseId must not be null"
        );

        this.quotaKey = Objects.requireNonNull(
                quotaKey,
                "quotaKey must not be null"
        );

        this.nodeId = Objects.requireNonNull(
                nodeId,
                "nodeId must not be null"
        );

        if (allocatedCapacity <= 0) {
            throw new IllegalArgumentException(
                    "allocatedCapacity must be greater than zero"
            );
        }

        this.issuedAt = Objects.requireNonNull(
                issuedAt,
                "issuedAt must not be null"
        );

        this.expiresAt = Objects.requireNonNull(
                expiresAt,
                "expiresAt must not be null"
        );

        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException(
                    "expiresAt must be after issuedAt"
            );
        }

        this.allocatedCapacity = allocatedCapacity;
        this.remainingCapacity = allocatedCapacity;
    }

    public boolean hasRemainingCapacity() {
        return remainingCapacity > 0;
    }

    public boolean isExpired(Instant currentTime) {
        Objects.requireNonNull(
                currentTime,
                "currentTime must not be null"
        );

        return !currentTime.isBefore(expiresAt);
    }

    public boolean canConsume(Instant currentTime) {
        return !isExpired(currentTime)
                && hasRemainingCapacity();
    }
    public void consume() {
        if (!hasRemainingCapacity()) {
            throw new IllegalStateException(
                    "lease has no remaining capacity"
            );
        }
        remainingCapacity--;
    }

    public void renew(Duration extension) {

        if (extension == null
                || extension.isZero()
                || extension.isNegative()) {
            throw new IllegalArgumentException(
                    "extension must be greater than zero"
            );
        }

        expiresAt = expiresAt.plus(extension);
    }
}
