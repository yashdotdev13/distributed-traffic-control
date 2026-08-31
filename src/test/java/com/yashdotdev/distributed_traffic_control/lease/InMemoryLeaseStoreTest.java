package com.yashdotdev.distributed_traffic_control.lease;

import com.yashdotdev.distributed_traffic_control.policy.PolicyStatus;
import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicy;
import com.yashdotdev.distributed_traffic_control.policy.TrafficPolicyType;
import com.yashdotdev.distributed_traffic_control.quota.QuotaKey;
import com.yashdotdev.distributed_traffic_control.traffic.TrafficSubject;
import com.yashdotdev.distributed_traffic_control.traffic.TrafficSubjectType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryLeaseStoreTest {

    @Test
    void shouldSaveAndFindLease() {

        InMemoryLeaseStore leaseStore =
                new InMemoryLeaseStore();

        QuotaKey quotaKey = createQuotaKey();

        QuotaLease lease = createLease(quotaKey);

        leaseStore.save(lease);

        Optional<QuotaLease> result =
                leaseStore.find(quotaKey);

        assertTrue(result.isPresent());
        assertSame(lease, result.get());
    }

    @Test
    void shouldReturnEmptyWhenLeaseDoesNotExist() {

        InMemoryLeaseStore leaseStore =
                new InMemoryLeaseStore();

        QuotaKey quotaKey = createQuotaKey();

        Optional<QuotaLease> result =
                leaseStore.find(quotaKey);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldRemoveLease() {

        InMemoryLeaseStore leaseStore =
                new InMemoryLeaseStore();

        QuotaKey quotaKey = createQuotaKey();

        QuotaLease lease = createLease(quotaKey);

        leaseStore.save(lease);

        leaseStore.remove(quotaKey);

        assertTrue(
                leaseStore.find(quotaKey).isEmpty()
        );
    }

    @Test
    void shouldReplaceExistingLeaseForSameQuota() {

        InMemoryLeaseStore leaseStore =
                new InMemoryLeaseStore();

        QuotaKey quotaKey = createQuotaKey();

        QuotaLease firstLease =
                new QuotaLease(
                        "lease-1",
                        quotaKey,
                        "node-1",
                        10,
                        Instant.parse("2026-08-28T10:00:00Z"),
                        Instant.parse("2026-08-28T10:01:00Z")
                );

        QuotaLease secondLease =
                new QuotaLease(
                        "lease-2",
                        quotaKey,
                        "node-1",
                        20,
                        Instant.parse("2026-08-28T10:00:00Z"),
                        Instant.parse("2026-08-28T10:02:00Z")
                );

        leaseStore.save(firstLease);
        leaseStore.save(secondLease);

        Optional<QuotaLease> result =
                leaseStore.find(quotaKey);

        assertTrue(result.isPresent());
        assertSame(secondLease, result.get());
    }

    private QuotaKey createQuotaKey() {

        TrafficPolicy policy = new TrafficPolicy(
                "test-policy",
                "Test Policy",
                TrafficPolicyType.TOKEN_BUCKET,
                PolicyStatus.ACTIVE,
                100,
                10,
                Instant.parse("2026-08-28T09:00:00Z")
        );

        return new QuotaKey(
                policy.getPolicyId(),
                new TrafficSubject(
                        "user-123",
                        TrafficSubjectType.USER
                ),
                "/api/orders"
        );
    }


    private QuotaLease createLease(
            QuotaKey quotaKey
    ) {

        return new QuotaLease(
                "lease-1",
                quotaKey,
                "node-1",
                10,
                Instant.parse("2026-08-28T10:00:00Z"),
                Instant.parse("2026-08-28T10:01:00Z")
        );
    }
}
