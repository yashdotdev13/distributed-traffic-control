# Problem Statement

## 1. Background

Modern APIs are rarely deployed as a single application instance.

As traffic increases, API gateways and backend services are typically deployed across multiple nodes to provide horizontal scalability, high availability, and fault tolerance. Incoming requests may be distributed across these nodes by a load balancer, reverse proxy, API gateway, Kubernetes Service, service mesh, or another traffic-routing component.

A single API consumer may therefore send requests that are processed by different gateway nodes over time.

Consider the following deployment:

```text
                              ┌─────────────┐
                              │   Client    │
                              └──────┬──────┘
                                     │
                                     ▼
                              ┌─────────────┐
                              │ Load Balancer│
                              └──────┬──────┘
                                     │
                 ┌───────────────────┼───────────────────┐
                 │                   │                   │
                 ▼                   ▼                   ▼
          ┌─────────────┐      ┌─────────────┐     ┌─────────────┐
          │  Gateway A  │      │  Gateway B  │     │  Gateway C  │
          └─────────────┘      └─────────────┘     └─────────────┘
```

When API traffic is distributed in this way, enforcing a shared traffic policy becomes more complicated.

For example, an organization may define the following quota for a client:

> The client is allowed to make 10,000 requests per minute.

The important requirement is that the client should be allowed to make **10,000 requests per minute across the entire distributed gateway fleet**, not 10,000 requests per minute on every individual gateway node.

This creates a distributed coordination problem.

All gateway nodes must collectively enforce the same global policy even though they process requests independently.

---

## 2. The Core Problem

Rate limiting is relatively straightforward when all requests are processed by a single application instance.

A single instance can maintain the state required to determine whether a request should be allowed or rejected.

For example:

```text
Client
   │
   ▼
┌──────────────────────┐
│ Single Gateway Node  │
│                      │
│ Requests: 7,532      │
│ Limit:    10,000     │
└──────────┬───────────┘
           │
           ▼
        Allow
```

Because every request passes through the same node, that node has a complete view of quota consumption.

The situation changes when the API gateway is horizontally scaled.

Consider three gateway nodes serving the same client:

```text
                              Client
                                 │
                                 ▼
                          Load Balancer
                                 │
                 ┌───────────────┼───────────────┐
                 │               │               │
                 ▼               ▼               ▼
          ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
          │  Gateway A  │ │  Gateway B  │ │  Gateway C  │
          └─────────────┘ └─────────────┘ └─────────────┘
```

Suppose the intended global policy is:

> 10,000 requests per minute

If every gateway independently applies that same limit locally, the effective behavior becomes:

```text
Gateway A → Local limit: 10,000 requests/minute
Gateway B → Local limit: 10,000 requests/minute
Gateway C → Local limit: 10,000 requests/minute
```

The client could potentially consume:

```text
Gateway A → 10,000 requests
Gateway B → 10,000 requests
Gateway C → 10,000 requests
────────────────────────────────
Total     → 30,000 requests
```

The intended global quota of 10,000 requests per minute has now been exceeded by a factor of three.

Every individual gateway may believe that it is enforcing the policy correctly.

However, none of the nodes has a complete view of the global quota consumption.

The problem can therefore be summarized as:

> How can multiple independently operating gateway nodes enforce a single shared API traffic quota without allowing the quota to multiply as the number of gateway nodes increases?

---

## 3. Why Purely Local Rate Limiting Is Insufficient

A purely local rate limiter maintains traffic-control state independently on every gateway node.

For example:

```text
┌─────────────────────┐
│      Gateway A      │
│                     │
│ Local Counter: 750  │
│ Local Limit: 10,000 │
└─────────────────────┘


┌─────────────────────┐
│      Gateway B      │
│                     │
│ Local Counter: 920  │
│ Local Limit: 10,000 │
└─────────────────────┘


┌─────────────────────┐
│      Gateway C      │
│                     │
│ Local Counter: 630  │
│ Local Limit: 10,000 │
└─────────────────────┘
```

There is no coordination between the nodes.

Each gateway can only observe the requests that arrive at that particular node.

As a result, each node has an incomplete view of global traffic consumption.

This creates several problems.

### 3.1 The Same Client Can Consume Capacity Across Multiple Nodes

A client can send requests that are distributed across different gateway nodes.

For example:

```text
Request 1  ─────────────► Gateway A
Request 2  ─────────────► Gateway B
Request 3  ─────────────► Gateway C
Request 4  ─────────────► Gateway A
Request 5  ─────────────► Gateway B
```

Each gateway only updates its own local state.

No individual gateway knows the total number of requests made by the client across the entire system.

### 3.2 Scaling Can Increase the Effective Quota

Suppose a policy is configured as:

> 1,000 requests per minute

With one gateway node:

```text
1 Gateway × 1,000 local requests = 1,000 effective requests
```

With five independently configured gateway nodes:

```text
5 Gateways × 1,000 local requests = 5,000 potential requests
```

Adding infrastructure capacity unintentionally changes the behavior of the traffic policy.

The effective quota now depends on the number of running gateway nodes.

This is not acceptable when the configured policy is intended to represent a shared global limit.

### 3.3 Gateway Nodes Cannot Independently Enforce a Global Policy

A global policy requires a shared understanding of available capacity.

A purely local gateway does not know:

- How much quota has been consumed by other gateway nodes.
- How much capacity remains globally.
- Whether another gateway is currently consuming the same client's quota.
- Whether the number of gateway nodes has changed.
- Whether another gateway has failed while holding locally maintained state.

Without coordination, independently operating nodes cannot reliably enforce a single shared quota.

### 3.4 Failures Can Cause State Inconsistency

Local traffic-control state can also be affected by failures and restarts.

For example:

```text
Gateway A
   │
   ├── Local quota state exists in memory
   │
   ▼
Gateway crashes
   │
   ▼
Local state is lost
```

After restarting, the gateway may not know how much quota was previously consumed unless state is recovered or coordinated externally.

This introduces additional questions about:

- State recovery.
- Gateway restarts.
- Lost local capacity.
- Reconciliation.
- Duplicate capacity allocation.

Therefore, purely local rate limiting is insufficient when a traffic policy must apply consistently across a distributed fleet of gateway nodes.

---

## 4. The Naive Centralized Approach

One possible solution is to coordinate every incoming request through a centralized rate-limiting system.

The request flow could look like this:

```text
Client Request
      │
      ▼
┌──────────────┐
│ Gateway Node │
└──────┬───────┘
       │
       ▼
┌──────────────────────┐
│ Central Rate Limit   │
│ Store / Coordinator  │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│ Atomic Rate Limit    │
│ Operation            │
└──────────┬───────────┘
           │
      ┌────┴────┐
      │         │
      ▼         ▼
    Allow     Reject
```

A centralized system could maintain the global rate-limit state.

Every gateway would communicate with the same shared dependency, and every request would perform an atomic operation against globally shared state.

For example:

```text
Gateway A ────────┐
                  │
Gateway B ────────┼────► Shared Rate Limit Store
                  │
Gateway C ────────┘
```

This provides stronger coordination because every request contributes to a globally shared view of quota consumption.

However, the approach introduces centralized coordination directly into the critical request path.

Every request may require:

1. A network call to a shared dependency.
2. An atomic operation against centralized state.
3. A response from the centralized system.
4. A decision before the original request can continue.

The request path becomes:

```text
Incoming Request
       │
       ▼
Gateway
       │
       ▼
Network Call
       │
       ▼
Centralized Rate Limit Operation
       │
       ▼
Network Response
       │
       ▼
Allow / Reject
```

This creates additional latency and makes the availability of the centralized dependency important for every request.

---

## 5. Problems With Per-Request Centralized Coordination

Centralized coordination can provide a strong shared view of quota consumption, but it creates new distributed-systems challenges.

### 5.1 The Shared Dependency Is on the Critical Path

Every request depends on communication with the centralized rate-limiting component.

If the dependency becomes slow:

```text
Gateway
   │
   ▼
Central Store
   │
   └── Slow response
         │
         ▼
Request latency increases
```

The rate-limiting infrastructure can directly affect the latency of otherwise healthy API requests.

### 5.2 High Traffic Produces High Coordination Volume

Suppose the gateway fleet processes:

> 100,000 requests per second

A per-request centralized coordination model may require approximately the same order of magnitude of coordination operations:

```text
100,000 incoming requests/second
              │
              ▼
100,000 rate-limit coordination operations/second
```

The centralized dependency must now scale with the request traffic of the entire gateway fleet.

As traffic increases, the shared coordination component can become a bottleneck.

### 5.3 The Centralized Component Can Become an Availability Dependency

If the centralized rate-limiting system becomes unavailable, gateway nodes must decide what to do.

Possible approaches include:

```text
Central Coordinator Unavailable
              │
      ┌───────┼────────┐
      │       │        │
      ▼       ▼        ▼
   Fail     Fail     Use Local
   Closed   Open     Capacity
```

Each strategy has different consequences.

**Fail closed** means rejecting requests when the system cannot verify quota availability.

This preserves stricter enforcement but can reduce API availability.

**Fail open** means allowing requests when the centralized dependency is unavailable.

This preserves API availability but can allow quota violations.

**Use locally available capacity** allows a gateway to continue operating temporarily using previously coordinated state.

This can improve availability while still providing bounded behavior, but requires a more sophisticated distributed coordination model.

### 5.4 Centralized Coordination Can Become a Scalability Bottleneck

The centralized component receives coordination traffic from all gateway nodes.

```text
              Gateway A ──┐
                          │
              Gateway B ──┤
                          │
              Gateway C ──┼────► Central Coordinator
                          │
              Gateway D ──┤
                          │
              Gateway E ──┘
```

As the number of gateway nodes and request volume increase, the centralized component must handle increasing load.

The goal of the system should therefore be to avoid making centralized coordination proportional to every incoming request whenever possible.

---

## 6. The Distributed Systems Challenge

The central challenge is balancing two competing requirements.

### 6.1 Requirement One: Low-Latency Local Decisions

Gateway nodes should be able to make traffic-control decisions quickly.

Ideally, the common request path should look like this:

```text
Incoming Request
       │
       ▼
┌─────────────────────┐
│    Gateway Node     │
│                     │
│ Local Policy Check  │
│ Local Quota Check   │
└──────────┬──────────┘
           │
      ┌────┴────┐
      │         │
      ▼         ▼
    Allow     Reject
```

The decision is made locally without requiring a network call for every request.

This provides several benefits:

- Lower request latency.
- Reduced load on centralized infrastructure.
- Better horizontal scalability.
- Reduced coordination overhead.
- Improved ability to continue operating during temporary control-plane failures.

### 6.2 Requirement Two: Shared Global Quota Enforcement

At the same time, all gateway nodes must collectively respect the same global policy.

```text
                  Shared Global Quota
                         │
       ┌─────────────────┼─────────────────┐
       │                 │                 │
       ▼                 ▼                 ▼
  Gateway A         Gateway B         Gateway C
       │                 │                 │
       └──────── Must collectively respect ┘
                  the same quota
```

The system must prevent a global limit from becoming independent per node.

The challenge is therefore:

> How can gateway nodes make decisions locally while ensuring that the total capacity available across the entire gateway fleet remains controlled?

This is the fundamental distributed coordination problem addressed by this project.

---

## 7. Proposed Product Direction

The project will explore a **control-plane and data-plane architecture**.

The purpose of this separation is to keep centralized coordination out of the normal request path whenever possible while still maintaining a coordinated global view of traffic-control policies and capacity.

The high-level architecture is:

```text
                         ┌─────────────────────────┐
                         │      Control Plane      │
                         │                         │
                         │  Policy Management      │
                         │  Quota Coordination     │
                         │  Capacity Allocation    │
                         │  Gateway Coordination   │
                         └────────────┬────────────┘
                                      │
                           Policies / Quota Leases
                                      │
                 ┌────────────────────┼────────────────────┐
                 │                    │                    │
                 ▼                    ▼                    ▼
          ┌─────────────┐      ┌─────────────┐      ┌─────────────┐
          │  Gateway A  │      │  Gateway B  │      │  Gateway C  │
          │             │      │             │      │             │
          │ Local State │      │ Local State │      │ Local State │
          │ Local Quota │      │ Local Quota │      │ Local Quota │
          └──────┬──────┘      └──────┬──────┘      └──────┬──────┘
                 │                    │                    │
                 ▼                    ▼                    ▼
              Requests             Requests             Requests
```

The control plane and data plane have different responsibilities.

---

## 8. Control Plane

The control plane is responsible for coordination and management.

It maintains the system-level understanding required to distribute traffic-control policies and quota capacity across the gateway fleet.

Its responsibilities may include:

- Creating and managing traffic-control policies.
- Maintaining global policy configuration.
- Versioning policies.
- Managing gateway registration or membership.
- Coordinating global quota allocation.
- Issuing quota leases.
- Tracking lease expiration.
- Handling lease renewal.
- Propagating policy updates.
- Collecting operational information.
- Managing gateway coordination state.

The control plane is not intended to make the allow or reject decision for every incoming API request.

Instead, it provides the coordination required for gateway nodes to make most decisions locally.

---

## 9. Data Plane

The data plane is responsible for evaluating incoming API traffic.

Each gateway node maintains enough locally available information to evaluate traffic without contacting the control plane for every request.

A gateway node may perform the following steps:

```text
Incoming Request
       │
       ▼
Identify Client / Tenant / API Key
       │
       ▼
Find Applicable Traffic Policy
       │
       ▼
Check Local Quota Capacity
       │
       ▼
┌─────────────────────────┐
│ Is Local Capacity       │
│ Available?              │
└────────────┬────────────┘
             │
       ┌─────┴─────┐
       │           │
      Yes          No
       │           │
       ▼           ▼
    Allow       Request More
    Request     Capacity
                  │
                  ▼
             Control Plane
```

The data plane should be optimized for low-latency request processing.

The primary request path should use locally available state whenever sufficient capacity exists.

Communication with the control plane should occur when coordination is necessary, rather than for every request.

---

## 10. Quota Leasing

The initial design direction for the project is based on **bounded quota leasing**.

Instead of requiring every request to consume quota directly from a centralized store, the control plane allocates a bounded amount of capacity to individual gateway nodes.

Consider a global quota of:

> 10,000 requests per minute

The control plane could distribute portions of this capacity:

```text
                     Global Quota
                10,000 requests/minute
                           │
                           ▼
                 ┌──────────────────┐
                 │ Quota Coordinator│
                 └────────┬─────────┘
                          │
             Allocates bounded capacity
                          │
          ┌───────────────┼───────────────┐
          │               │               │
          ▼               ▼               ▼
     Gateway A        Gateway B        Gateway C
     Lease: 3,000     Lease: 4,000     Lease: 3,000
```

Each gateway can then consume its locally allocated capacity independently.

For Gateway A:

```text
Lease Capacity: 3,000

Request
   │
   ▼
Local Capacity Available?
   │
   ├── Yes ──► Consume 1 unit ──► Allow
   │
   └── No ───► Coordinate for additional capacity
```

Most requests can therefore be handled locally.

The gateway only needs to coordinate when its locally allocated capacity becomes insufficient or when the state of the lease changes.

---

## 11. Why Bounded Capacity Matters

The important property of the design is that gateway nodes should not receive unlimited authority to consume the global quota.

Each gateway receives only a bounded amount of capacity.

For example:

```text
Global Capacity: 10,000

Allocated:
Gateway A → 3,000
Gateway B → 4,000
Gateway C → 3,000

Total Allocated → 10,000
```

The gateway nodes can make independent decisions within their allocated capacity.

However, they cannot independently consume unlimited global quota.

This allows the system to reduce coordination on the request path while maintaining control over how much capacity is distributed throughout the system.

The precise implementation details will be determined in later phases.

These details include:

- Lease size.
- Lease duration.
- Renewal strategy.
- Allocation strategy.
- Expiration behavior.
- Reclamation of unused capacity.
- Handling of failed gateway nodes.
- Handling of control-plane failures.

---

## 12. The Central Design Question

The central design question for `distributed-traffic-control` is:

> How can a distributed system enforce a shared global API quota across multiple gateway nodes while allowing most traffic-control decisions to be made locally?

The project must balance several competing concerns.

### 12.1 Consistency and Latency

Stronger coordination can provide a more accurate global view.

However, stronger coordination may require additional communication and increase request latency.

### 12.2 Scalability and Central Coordination

Centralized coordination simplifies some consistency problems.

However, coordinating every request through a shared dependency can create a scalability bottleneck.

### 12.3 Availability and Strict Enforcement

During failures, the system must decide whether to prioritize:

- Strict quota enforcement.
- Continued API availability.
- Controlled degraded operation.

There is no universally correct answer.

The appropriate behavior depends on the guarantees provided by the system.

### 12.4 Local Autonomy and Global Control

Gateway nodes should be able to operate independently for common request processing.

At the same time, the control plane must maintain enough coordination to prevent uncontrolled global quota consumption.

The architecture must balance these two requirements.

---

## 13. Intended System Guarantee

The initial version of the system will not attempt to maintain perfectly synchronized global state for every individual request.

Perfect per-request synchronization would require stronger coordination on the request path and would reduce the benefits of local decision-making.

Instead, the system will focus on **bounded distributed quota enforcement**.

The intended system property is:

> Global quota consumption is controlled through bounded capacity allocation, allowing gateway nodes to make low-latency local decisions while limiting the amount of independently consumable capacity distributed across the gateway fleet.

This means the system intentionally explores a trade-off.

The goal is not to eliminate all distributed coordination.

The goal is to move coordination away from the common request path and perform it when capacity allocation or policy synchronization requires it.

The exact consistency guarantees and bounds on quota overshoot will be formally defined in subsequent requirements and architecture documents.

---

## 14. Failure Scenarios

Distributed traffic-control systems must explicitly define behavior during failures.

The project will consider scenarios including the following.

### 14.1 Gateway Node Failure

A gateway may crash while holding unused locally allocated capacity.

```text
Gateway A
   │
   ├── Lease Capacity: 3,000
   ├── Consumed: 1,200
   └── Remaining: 1,800
           │
           ▼
        CRASH
```

The system must determine what happens to the remaining capacity.

Questions include:

- Can the capacity be reclaimed?
- When can it be reclaimed?
- What happens if the gateway recovers?
- How can duplicate capacity consumption be prevented?

### 14.2 Control Plane Failure

The control plane may become temporarily unavailable.

```text
Gateway
   │
   ▼
Request Additional Capacity
   │
   ▼
Control Plane
   │
   X
Unavailable
```

A gateway may still have locally available capacity.

The system must define whether the gateway can continue processing requests using that capacity.

### 14.3 Network Partition

A gateway may remain healthy but temporarily lose communication with the control plane.

```text
┌─────────────┐      Network Failure      ┌─────────────────┐
│   Gateway   │ ─────────── X ────────── │ Control Plane   │
└─────────────┘                          └─────────────────┘
```

The gateway must determine whether its locally held quota remains valid.

### 14.4 Gateway Restart

A gateway may restart after receiving quota capacity.

The system must determine how local state is recovered and whether previously issued capacity can still be safely used.

These scenarios are important because traffic-control correctness cannot be defined only for healthy systems.

The system behavior during degraded conditions is part of the product design.

---

## 15. Policy Propagation

Traffic-control policies may change while gateway nodes are actively processing requests.

For example:

```text
Policy Version 12
Limit: 10,000 requests/minute

            │
            ▼

Policy Updated

            │
            ▼

Policy Version 13
Limit: 5,000 requests/minute
```

The system must determine how updated policies are propagated to gateway nodes.

Important questions include:

- How are policies versioned?
- How does a gateway identify a newer policy?
- How are stale policies handled?
- What happens to capacity allocated under an older policy?
- How quickly must a policy update become effective?
- What happens when a gateway is temporarily disconnected?

The exact propagation mechanism will be designed in later phases.

However, policy versioning and distributed configuration consistency are core problems within the scope of the project.

---

## 16. Observability Requirements

A distributed traffic-control platform must provide visibility into both request decisions and distributed coordination.

The system should eventually expose information about:

- Total requests evaluated.
- Allowed requests.
- Rejected requests.
- Quota consumption.
- Locally available capacity.
- Capacity allocation.
- Lease renewal.
- Lease expiration.
- Gateway registration.
- Gateway health.
- Policy versions.
- Policy propagation delays.
- Control-plane failures.
- Data-plane coordination failures.

For example:

```text
                    Observability
                         │
          ┌──────────────┼──────────────┐
          │              │              │
          ▼              ▼              ▼
     Gateway A       Gateway B      Control Plane
          │              │              │
          └──────────────┼──────────────┘
                         ▼
                 Metrics / Monitoring
```

Observability is necessary to understand how distributed capacity is allocated and consumed.

It is also required to investigate failures and validate the behavior of the system under load.

---

## 17. Reproducible Distributed Experiments

The project should not only describe distributed behavior theoretically.

It should provide reproducible experiments demonstrating important scenarios.

These scenarios should eventually include:

### Multiple Gateway Nodes

Traffic is distributed across several gateway nodes while enforcing the same policy.

### Uneven Traffic Distribution

One gateway receives significantly more traffic than other nodes.

The system should demonstrate how capacity allocation behaves under this imbalance.

### Quota Exhaustion

A gateway exhausts its locally allocated capacity and must coordinate for additional capacity.

### Gateway Failure

A gateway fails while holding unused capacity.

The system behavior should be observable and reproducible.

### Control Plane Unavailability

The control plane becomes unavailable while gateway nodes continue processing traffic.

The system should demonstrate its defined degraded behavior.

### Policy Update

A traffic policy is modified while multiple gateway nodes are actively processing requests.

### Lease Expiration

Locally allocated capacity reaches its expiration boundary and must no longer be used unless renewed according to the system's guarantees.

These experiments will provide measurable evidence for the architectural decisions made by the project.

---

## 18. Product Boundary

`distributed-traffic-control` is a distributed traffic-control platform.

It is not intended to become a complete API gateway or API management product.

Its primary responsibility is to determine whether traffic should be allowed or rejected according to configured traffic-control policies and distributed quota state.

The following concerns are outside the primary responsibility of the project:

- API business logic.
- Request routing.
- Load balancing.
- Full authentication implementation.
- Application-level authorization.
- Request transformation.
- Response transformation.
- API documentation hosting.
- API monetization.
- Service discovery implementation.
- Complete API gateway functionality.

The platform may eventually integrate with or simulate gateway components, but these capabilities are not the core problem being solved.

The project should remain focused on distributed traffic control and quota coordination.

---

## 19. Project Focus

The primary objective of this project is not simply to implement a token bucket, fixed window, sliding window, or any other rate-limiting algorithm.

Those algorithms are implementation mechanisms.

The primary engineering value of `distributed-traffic-control` comes from solving the distributed problems that emerge when a shared traffic policy must be enforced across multiple independently operating nodes.

The project focuses on:

- Distributed state management.
- Global quota coordination.
- Bounded capacity allocation.
- Local versus centralized decision-making.
- Gateway coordination.
- Lease management.
- Failure handling.
- State recovery.
- Policy versioning.
- Policy propagation.
- Consistency trade-offs.
- Availability trade-offs.
- Horizontal scalability.
- Observability.
- Reproducible distributed-system experiments.

The selected rate-limiting algorithm is therefore only one component of the larger architecture.

The core value of the system is the coordination model used to enforce shared traffic policies across a distributed gateway fleet.

---

## 20. Success Criteria

The project will be considered successful if the initial system demonstrates the following capabilities.

### 20.1 Shared Policy Enforcement

Multiple gateway nodes can enforce the same traffic-control policy.

Adding additional gateway nodes must not simply multiply the configured global quota.

### 20.2 Local Request Decisions

The common request path can make allow or reject decisions using locally available state.

The system should avoid centralized coordination for every incoming request whenever possible.

### 20.3 Bounded Capacity Allocation

Gateway nodes receive a bounded amount of globally coordinated capacity.

No individual gateway should be able to consume unlimited global quota independently.

### 20.4 Distributed Coordination

Gateway nodes can coordinate with the control plane when they require additional capacity or updated policy information.

### 20.5 Defined Failure Behavior

The system defines explicit behavior for:

- Gateway failures.
- Gateway restarts.
- Control-plane failures.
- Network communication failures.
- Lease expiration.
- Policy propagation failures.

### 20.6 Policy Versioning

Traffic-control policies can be versioned and updated across distributed gateway nodes.

### 20.7 Operational Visibility

The system provides sufficient observability to understand:

- Request decisions.
- Quota consumption.
- Capacity allocation.
- Gateway state.
- Lease state.
- Policy versions.
- Coordination failures.

### 20.8 Distributed Testing

The architecture can be validated through reproducible scenarios involving multiple gateway nodes and realistic failure conditions.

---

## 21. Summary

`distributed-traffic-control` addresses the distributed coordination problem of enforcing shared API traffic quotas across multiple independently operating gateway nodes.

A purely local rate limiter is insufficient because every gateway maintains only an isolated view of traffic consumption. As the gateway fleet scales, independently enforced local limits can cause the effective global quota to increase.

A centralized per-request coordination model provides a stronger shared view of quota consumption, but introduces a centralized dependency into the critical request path and can create latency, scalability, and availability concerns.

The proposed direction is a control-plane and data-plane architecture.

The control plane manages policies, distributed coordination, and bounded capacity allocation. Gateway nodes in the data plane use locally available capacity to make low-latency allow or reject decisions and coordinate with the control plane only when necessary.

The central engineering challenge of the project is therefore:

> How can a distributed system provide low-latency local traffic-control decisions while maintaining bounded and coordinated enforcement of a shared global API quota?

The remainder of the project will define the exact product scope, functional requirements, non-functional requirements, API contracts, consistency guarantees, distributed architecture, data model, coordination protocol, failure behavior, and experimental validation strategy required to answer this question.