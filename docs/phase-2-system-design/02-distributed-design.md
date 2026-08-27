# Distributed Design

## 1. Purpose

This document defines the distributed systems design for API Traffic Control.

The focus of this document is how the system behaves when multiple API Gateway instances process traffic concurrently.

The design addresses:

- distributed rate limiting
- hierarchical quota enforcement
- token leasing
- policy propagation
- adaptive capacity allocation
- consistency and failure handling
- hot-key mitigation
- idempotency and retry behavior
- multi-instance coordination

The initial implementation will begin as a modular monolith, but the internal design must allow these responsibilities to evolve into independently deployable components.

---

## 2. Distributed System Problem

A single gateway instance can maintain a local counter and enforce a rate limit easily.

The problem becomes significantly harder when traffic is distributed across multiple gateway instances.

Example:

- Tenant limit: 10,000 requests per minute
- Gateway instances: 10
- Each gateway independently receives requests for the same tenant

If every gateway independently allows 10,000 requests per minute, the tenant could consume up to 100,000 requests per minute.

Therefore, quota state cannot be treated as completely independent local state.

The system requires coordination while avoiding a centralized remote call for every request.

The core design principle is:

> Strong coordination should happen less frequently than request processing.

This is the reason for introducing token leasing.

---

# 3. Core Distributed Components

The logical distributed architecture contains the following components.

## 3.1 API Gateway Instances

Gateway instances are part of the Data Plane.

Responsibilities:

- receive API requests
- identify tenant and client
- evaluate locally available policy
- consume locally leased tokens
- reject requests when local capacity is unavailable
- request additional capacity when necessary
- emit metrics and decision events

Gateway instances must not require a remote database query for every request.

---

## 3.2 Policy Store

The Policy Store contains durable traffic control configuration.

It stores:

- tenant policies
- client policies
- endpoint policies
- quota hierarchy
- traffic limits
- burst configuration
- policy version information

The Policy Store is part of the Control Plane.

It is not used directly in the synchronous request path.

---

## 3.3 Policy Distribution Component

The Policy Distribution Component propagates policy changes to gateway instances.

Responsibilities:

- detect policy changes
- publish new policy versions
- invalidate stale policy caches
- distribute policy snapshots or incremental updates
- track gateway acknowledgement where required

The gateway should continue serving requests using its last known valid policy if the distribution channel is temporarily unavailable.

---

## 3.4 Quota Coordinator

The Quota Coordinator manages shared capacity.

Responsibilities:

- maintain distributed quota state
- allocate token leases
- prevent excessive global quota consumption
- reclaim expired leases
- support hierarchical quota checks

The coordinator is contacted when a gateway needs additional tokens.

It is not contacted for every successful request.

---

## 3.5 Adaptive Capacity Allocator

The Adaptive Capacity Allocator determines how many tokens should be granted to a gateway.

A fixed lease size can create problems:

- small leases increase coordinator traffic
- large leases increase unused capacity
- large leases may cause unfair distribution
- traffic patterns can change rapidly

The allocator uses observed demand to determine a suitable lease size.

---

# 4. Hierarchical Quota Model

API Traffic Control supports quotas at multiple levels.

A request can be evaluated against:

1. Global quota
2. Tenant quota
3. Client quota
4. Endpoint quota

Example:

```text
Global Capacity
      |
      v
Tenant Capacity
      |
      v
Client Capacity
      |
      v
Endpoint Capacity
      |
      v
Request Decision
```

A request is allowed only when every applicable quota level has sufficient capacity.

Conceptually:

```text
ALLOW =
    global_capacity_available
    AND tenant_capacity_available
    AND client_capacity_available
    AND endpoint_capacity_available
```

Not every deployment must enable every level.

The policy determines which quota levels apply to a request.

---

# 5. Token Leasing

## 5.1 Motivation

Direct distributed coordination for every request creates several problems:

- increased latency
- high coordinator load
- network dependency in the hot path
- poor availability during coordinator failures

Token leasing reduces coordination frequency.

Instead of asking:

```text
Can I process this request?
```

for every request, a gateway asks:

```text
Can you grant me N requests worth of capacity?
```

The gateway then consumes that capacity locally.

---

## 5.2 Lease Flow

Example:

```text
Gateway
   |
   | Request 500 tokens
   v
Quota Coordinator
   |
   | Validate available quota
   | Reserve 500 tokens
   |
   v
Gateway receives lease
   |
   | Local request processing
   |
   v
Consume leased tokens
```

The gateway does not need to contact the coordinator while local leased tokens remain available.

---

## 5.3 Lease Structure

A logical lease contains:

```text
leaseId
quotaKey
gatewayId
allocatedTokens
remainingTokens
issuedAt
expiresAt
policyVersion
```

Definitions:

- `leaseId`: unique identifier for the lease
- `quotaKey`: identifies the quota being leased
- `gatewayId`: gateway instance receiving capacity
- `allocatedTokens`: total granted capacity
- `remainingTokens`: locally available capacity
- `issuedAt`: allocation time
- `expiresAt`: lease expiry time
- `policyVersion`: policy version used during allocation

---

## 5.4 Local Consumption

Gateway request processing should remain local.

Conceptually:

```text
if remainingTokens > 0:
    atomically decrement remainingTokens
    allow request
else:
    request or renew lease
```

The decrement operation must be thread-safe because one gateway can process many concurrent requests.

---

# 6. Lease Renewal

A gateway should not wait until its token count reaches zero before requesting more capacity.

The system should use a renewal threshold.

Example:

```text
Allocated tokens: 1,000
Renewal threshold: 20%
```

When remaining tokens reach:

```text
200
```

the gateway may asynchronously request another lease.

This reduces the probability that request processing pauses because the local lease is exhausted.

Conceptual flow:

```text
Request
   |
   v
Consume Local Token
   |
   +--> Remaining above threshold --> Continue
   |
   +--> Remaining below threshold --> Trigger async renewal
```

---

# 7. Lease Expiration

Leases must expire.

Without expiration, a failed gateway could permanently hold capacity that it never uses.

Example:

```text
Gateway A receives 5,000 tokens
Gateway A crashes
Gateway A never consumes 4,000 tokens
```

Lease expiration allows unused capacity to eventually return to the global pool.

The trade-off is temporary underutilization until the lease expires.

Therefore, lease duration must balance:

- failure recovery
- coordination overhead
- quota accuracy
- unused capacity

The initial implementation should use a configurable lease duration.

---

# 8. Oversubscription Control

Token leasing introduces a trade-off between availability and exact global quota enforcement.

Consider:

```text
Global quota: 10,000
Gateway A lease: 2,000
Gateway B lease: 2,000
Gateway C lease: 2,000
Gateway D lease: 2,000
Gateway E lease: 2,000
```

All capacity is distributed.

If additional gateways require capacity, they must wait for:

- token consumption to be reconciled where applicable
- lease expiration
- new quota window capacity

The Quota Coordinator must never grant more capacity than the configured quota allows unless explicit bounded oversubscription is enabled.

The default design is:

> Coordinator allocations are globally bounded.

This prevents unbounded multiplication of quota across gateway instances.

---

# 9. Adaptive Lease Sizing

A fixed lease size is inefficient for all traffic patterns.

The system should support adaptive lease sizing.

Example inputs:

- recent request rate
- current remaining tokens
- lease renewal frequency
- historical demand
- quota size
- gateway traffic share

A simple initial strategy can be:

```text
requestedTokens =
    observedRequestsPerSecond
    * targetLeaseDurationSeconds
```

The value should then be bounded:

```text
minLeaseSize <= requestedTokens <= maxLeaseSize
```

Example:

```text
Minimum lease: 100
Maximum lease: 5,000
```

Low traffic gateways receive smaller leases.

High traffic gateways receive larger leases.

This reduces unnecessary coordinator traffic while limiting capacity stranded on inactive gateways.

---

# 10. Policy Versioning

Traffic policies can change while leases are active.

Example:

```text
Tenant limit changes:
10,000 requests/minute
        |
        v
5,000 requests/minute
```

Existing leases may have been allocated using the previous policy.

Therefore, policy versions must be associated with:

- cached policy
- lease allocation
- policy update events

A gateway receiving a newer policy version should refresh its local policy state.

The coordinator should reject lease requests based on stale policy assumptions when a newer policy version is active.

---

# 11. Policy Propagation Strategy

The system uses a versioned policy distribution model.

Basic flow:

```text
Policy Updated
      |
      v
Persist New Version
      |
      v
Publish Policy Change Event
      |
      v
Gateway Receives Update
      |
      v
Refresh Local Policy Cache
```

Gateways should not reload all policies for every policy change.

The system should support targeted updates where possible.

Example:

```text
tenant: acme
policy version: 42
```

Only gateways serving that tenant may need the new policy immediately.

---

# 12. Gateway Local State

Each gateway maintains local state for:

```text
Policy Cache
Lease Cache
Local Token Counters
Recent Traffic Statistics
Renewal State
```

Conceptually:

```text
Gateway Instance
|
+-- Policy Cache
|
+-- Lease Manager
|     |
|     +-- Tenant Lease
|     +-- Client Lease
|     +-- Endpoint Lease
|
+-- Local Rate Limiter
|
+-- Metrics Collector
```

The local state should be disposable.

A gateway restart must not require manual reconstruction.

It should be able to:

1. load policies
2. request fresh leases
3. resume processing

---

# 13. Hot-Key Problem

Some tenants or endpoints may receive significantly more traffic than others.

Example:

```text
Tenant A: 100 requests/minute
Tenant B: 1,000,000 requests/minute
```

If every lease request for Tenant B targets one coordination key, that key may become a bottleneck.

The design must consider hot-key mitigation.

Possible approaches include:

- larger adaptive leases for hot keys
- local batching
- quota partitioning
- striped counters
- sharded coordination keys

The initial implementation should start with adaptive leases and local batching before introducing more complex sharding.

---

# 14. Concurrency Model

Multiple request threads can consume the same local lease concurrently.

The local token count must never become negative because of race conditions.

The implementation should use atomic operations or equivalent synchronization.

Conceptually:

```text
repeat:
    current = remainingTokens

    if current <= 0:
        reject or acquire capacity

    if compareAndSet(current, current - 1):
        allow request
```

The exact implementation may use Java concurrency primitives, depending on the module design.

The important invariant is:

> One local token must authorize at most one request.

---

# 15. Idempotency for Lease Requests

Lease requests can fail after the coordinator processes them.

Example:

```text
Gateway -> Coordinator: Request 1,000 tokens
Coordinator -> Gateway: 1,000 tokens granted
Network failure occurs
Gateway does not receive response
Gateway retries
```

Without idempotency, the retry could allocate another 1,000 tokens.

Therefore, every lease request should contain an idempotency identifier.

Example:

```text
requestId
gatewayId
quotaKey
requestedTokens
```

The coordinator must recognize repeated requests and return the previous allocation when appropriate.

---

# 16. Failure Scenario: Coordinator Unavailable

The gateway may temporarily lose access to the Quota Coordinator.

Behavior depends on local state.

## Case 1: Local lease still available

```text
Coordinator unavailable
+
Local tokens available
=
Continue processing requests
```

## Case 2: Local lease exhausted

The configured failure mode determines behavior.

### Fail Closed

Reject additional traffic.

```text
No coordinator
+
No local capacity
=
Reject
```

Advantages:

- protects quota guarantees
- prevents uncontrolled traffic

Disadvantages:

- lower availability

### Fail Open

Allow traffic temporarily.

Advantages:

- higher availability

Disadvantages:

- quota may be violated

The default policy for strict quota enforcement should be fail closed.

Fail-open behavior should require explicit policy configuration.

---

# 17. Failure Scenario: Gateway Crash

When a gateway crashes:

- its local tokens may remain unused
- no further requests can consume those tokens
- the coordinator eventually reclaims capacity after lease expiry

This creates bounded temporary capacity loss.

The maximum stranded capacity is related to:

```text
lease size
+
lease duration
```

Adaptive lease sizing helps reduce this impact for low-traffic gateways.

---

# 18. Failure Scenario: Policy Distribution Unavailable

If the policy distribution mechanism is unavailable, the gateway should continue using the last known valid policy.

The gateway must expose observability signals indicating that policy state may be stale.

Examples:

```text
policy_cache_age_seconds
policy_last_update_timestamp
policy_distribution_connected
```

The system should define a configurable maximum policy staleness period.

Beyond that period, stricter behavior may be applied depending on the policy criticality.

---

# 19. Network Partitions

Distributed systems can experience partial network failures.

Example:

```text
Gateway A <---X---> Quota Coordinator
Gateway B ---------> Quota Coordinator
```

Gateway A may continue using previously leased capacity while Gateway B can obtain new capacity.

This is expected behavior.

Token leasing intentionally provides a bounded period of local autonomy.

The lease duration determines how long a partitioned gateway can continue serving traffic without coordination.

---

# 20. Consistency Model

API Traffic Control does not require strong global synchronization for every request.

The consistency model is based on:

- strongly bounded coordinator allocation
- local eventual consumption of leased capacity
- versioned policy propagation
- lease expiration for failure recovery

The system prioritizes:

1. low request-path latency
2. high gateway availability
3. bounded global quota correctness

The system does not attempt to maintain a globally synchronized exact request counter after every request.

---

# 21. Distributed Decision Flow

The normal request flow is:

```text
Incoming Request
       |
       v
Identify Quota Hierarchy
       |
       v
Load Local Policy
       |
       v
Check Local Lease
       |
       +--> Tokens Available
       |        |
       |        v
       |      Consume
       |        |
       |        v
       |      Forward Request
       |
       +--> Tokens Unavailable
                |
                v
         Request/Renew Lease
                |
                +--> Granted --> Consume --> Forward
                |
                +--> Denied --> Reject
```

The coordinator is therefore outside the normal fast path whenever local capacity exists.

---

# 22. Initial Implementation Boundaries

The first implementation should not immediately introduce a fully distributed deployment.

Instead, the code should preserve distributed boundaries through interfaces.

Example conceptual modules:

```text
traffic-control
├── policy
├── quota
├── lease
├── allocation
├── gateway
├── metrics
└── persistence
```

Important interfaces may include:

```text
PolicyProvider
QuotaCoordinator
LeaseRepository
LeaseAllocator
TrafficDecisionEngine
```

The initial implementation can provide local implementations.

Later versions can replace them with:

- remote coordinator implementations
- Redis-backed implementations
- event-driven policy distribution
- distributed storage

The domain logic should not need major rewrites when these infrastructure implementations change.

---

# 23. Key Design Invariants

The following invariants must be preserved.

### Invariant 1

A local token can authorize only one request.

### Invariant 2

The coordinator must not allocate unlimited capacity beyond the configured quota.

### Invariant 3

Lease retries must be idempotent.

### Invariant 4

Expired leases must no longer be considered valid.

### Invariant 5

A policy update must have a monotonic version.

### Invariant 6

Gateway request processing must not depend on synchronous durable database access.

### Invariant 7

Coordinator failure must have deterministic fail-open or fail-closed behavior.

### Invariant 8

A gateway restart must not corrupt global quota state.

---

# 24. Metrics Required for Distributed Operation

The following metrics should be collected.

## Lease Metrics

```text
leases_requested_total
leases_granted_total
leases_denied_total
lease_renewal_latency
lease_expired_total
tokens_allocated_total
tokens_consumed_total
```

## Gateway Metrics

```text
requests_allowed_total
requests_rejected_total
local_tokens_remaining
policy_cache_hit_rate
policy_cache_age_seconds
```

## Coordinator Metrics

```text
quota_allocation_latency
active_leases
available_global_capacity
idempotency_replays
```

## Failure Metrics

```text
coordinator_connection_failures
policy_distribution_failures
fallback_decisions_total
stale_policy_requests_total
```

---

# 25. Future Evolution

The distributed architecture can evolve in stages.

## Stage 1

Single-process modular implementation.

- local coordinator abstraction
- in-memory policy cache
- local token lease implementation
- deterministic tests

## Stage 2

Multi-instance gateway deployment.

- shared quota coordinator
- remote lease allocation
- gateway identity
- lease expiration

## Stage 3

Event-driven policy distribution.

- versioned policy events
- targeted invalidation
- gateway synchronization

## Stage 4

Advanced allocation.

- adaptive lease sizing
- hot-key detection
- quota partitioning
- fairness algorithms

## Stage 5

Regional distribution.

- regional quota coordinators
- hierarchical capacity allocation
- cross-region policy replication

---

# 26. Summary

The distributed design is based on a simple principle:

> Coordinate capacity allocation globally, but consume capacity locally.

This allows the system to avoid a remote coordination call for every API request.

The main mechanism is token leasing:

```text
Global Quota
     |
     v
Quota Coordinator
     |
     | Allocate bounded capacity
     v
Gateway Lease
     |
     | Local atomic consumption
     v
API Request
```

This approach provides:

- low request-path latency
- reduced coordination traffic
- bounded distributed quota enforcement
- resilience to temporary coordinator failures
- controlled recovery after gateway failure
- a clear path from modular monolith to distributed deployment

The next implementation phase will convert these design boundaries into concrete domain models, interfaces, persistence choices, APIs, and execution flows.
