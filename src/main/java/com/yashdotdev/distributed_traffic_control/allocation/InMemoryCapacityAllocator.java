package com.yashdotdev.distributed_traffic_control.allocation;

import com.yashdotdev.distributed_traffic_control.lease.LeaseConsumptionResult;
import com.yashdotdev.distributed_traffic_control.lease.LeaseCoordinator;
import com.yashdotdev.distributed_traffic_control.lease.LeaseStore;
import com.yashdotdev.distributed_traffic_control.lease.QuotaLease;
import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicy;
import com.yashdotdev.distributed_traffic_control.quota.QuotaKey;
import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@RequiredArgsConstructor
public class InMemoryCapacityAllocator implements CapacityAllocator {

    private final LeaseCoordinator leaseCoordinator;
    private final AllocationStrategy allocationStrategy;
    private final AllocationProperties allocationProperties;
    private final LeaseStore leaseStore;
    private final Clock clock;

    @Override
    public Optional<QuotaLease> allocate(
            TrafficPolicy policy,
            QuotaKey quotaKey
    ) {
        Instant currentTime = clock.instant();

        Optional<QuotaLease> existingLease =
                leaseStore.find(quotaKey);

        if (existingLease.isPresent()
                && existingLease.get().canConsume(currentTime)) {
            return existingLease;
        }

        if (existingLease.isPresent()) {
            QuotaLease staleLease = existingLease.get();

            leaseStore.remove(quotaKey);

            leaseCoordinator.releaseLease(staleLease);
        }

        long allocationCapacity =
                allocationStrategy.determineCapacity(policy);

        Optional<QuotaLease> newLease =
                leaseCoordinator.acquireLease(
                        quotaKey,
                        allocationProperties.getNodeId(),
                        allocationCapacity,
                        allocationProperties.getLeaseDuration()
                );

        newLease.ifPresent(leaseStore::save);

        return newLease;
    }

    @Override
    public LeaseConsumptionResult tryConsume(
            QuotaLease lease,
            Instant currentTime
    ) {
        LeaseConsumptionResult result =
                leaseCoordinator.tryConsume(
                        lease,
                        allocationProperties.getNodeId(),
                        currentTime
                );

        if (!result.isConsumed()) {
            leaseStore.remove(lease.getQuotaKey());

            leaseCoordinator.releaseLease(lease);

            return result;
        }

        if (result.getRemainingCapacity() == 0) {
            leaseStore.remove(lease.getQuotaKey());

            leaseCoordinator.releaseLease(lease);
        }

        return result;
    }
}