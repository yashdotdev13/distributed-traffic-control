# Product Scope

## 1. Introduction

Distributed Traffic Control is a backend platform designed to define and enforce traffic-control policies across one or more gateway nodes.

The project focuses on the problem of controlling API traffic when requests are processed by multiple independently running nodes.

A simple local rate limiter can control traffic on a single application instance. However, when the application is horizontally scaled, multiple nodes may independently process requests for the same client or tenant.

The long-term objective of this project is to build a distributed traffic-control platform that can coordinate global traffic policies while keeping request evaluation as local as possible.

The product will be developed progressively.

The initial implementation will focus on local traffic-control capabilities and clean domain boundaries.

Distributed coordination, capacity allocation, policy propagation, and failure handling will be introduced in later phases.

---

## 2. Product Goal

The primary goal of Distributed Traffic Control is to enforce traffic-control policies across a distributed fleet of gateway nodes without requiring centralized coordination for every incoming request.

The platform should eventually support a model in which:

- A global traffic policy is defined centrally.
- Gateway nodes receive the information required to enforce that policy.
- Requests are evaluated locally whenever possible.
- Global capacity is coordinated across multiple gateway nodes.
- Policy changes can be propagated safely.
- Gateway failures and stale coordination state can be handled predictably.

The project is intended to focus on the distributed-systems challenges involved in traffic control rather than simply implementing a basic rate limiter.

---

## 3. In Scope

### 3.1 Traffic Policies

The system will support traffic-control policies that define how incoming traffic should be evaluated.

A policy will initially contain information such as:

- Unique policy identifier.
- Policy name.
- Traffic subject.
- Traffic-control algorithm.
- Configured capacity.
- Evaluation period or refill configuration.
- Policy status.
- Policy version.

An example policy may be conceptually represented as:

```text
Policy: client-standard

Traffic Subject: client-123
Algorithm: Token Bucket
Capacity: 1,000 requests
Refill Rate: 1,000 requests per minute
Status: ACTIVE
Version: 1
```

The policy model should be extensible.

The initial implementation may apply policies to a single traffic subject, while future versions should support additional traffic dimensions.

Possible future dimensions include:

- Client.
- API key.
- User.
- Tenant.
- IP address.
- Application.
- API route.
- Service.
- Geographic region.

The introduction of additional traffic dimensions should not require the core traffic-control architecture to be rewritten.

---

### 3.2 Traffic Subject Identification

The system must identify the entity against which traffic should be controlled.

The initial implementation will support a simple traffic subject identifier.

For example:

```text
X-Client-Id: client-123
```

The HTTP or gateway layer will be responsible for extracting this information from an incoming request.

The traffic-control domain should receive a normalized representation of the traffic subject.

The core domain must not depend directly on:

- HTTP headers.
- Spring MVC controllers.
- Servlet APIs.
- Gateway-specific request objects.

This separation will allow the traffic-control engine to be integrated with different request-processing environments in the future.

---

### 3.3 Policy Resolution

After identifying the traffic subject, the system must determine which traffic policy applies to the request.

The conceptual request flow is:

```text
Incoming Request
        |
        v
Identify Traffic Subject
        |
        v
Resolve Applicable Policy
        |
        v
Evaluate Traffic Capacity
        |
        v
Allow or Reject Request
```

The policy resolution mechanism should be abstracted from the enforcement logic.

The enforcement engine should not directly depend on a specific storage technology.

The initial implementation may use an in-memory policy source.

Future implementations may support:

- Relational databases.
- Redis.
- Distributed configuration stores.
- Control-plane APIs.
- Event-driven policy propagation.

---

### 3.4 Local Traffic Enforcement

The initial implementation will support local traffic enforcement.

A node receiving a request should be able to:

1. Identify the traffic subject.
2. Resolve the applicable policy.
3. Evaluate the request against the available local capacity.
4. Produce an allow or reject decision.
5. Return the decision to the request-processing layer.

The local enforcement engine will maintain traffic-control state locally during the initial implementation.

This stage is intended to establish a correct traffic-control domain before distributed coordination is introduced.

The core enforcement logic should remain independently testable.

---

### 3.5 Traffic-Control Algorithms

The platform will define an abstraction for traffic-control algorithms.

The architecture should not assume that every policy uses the same algorithm.

The initial implementation will focus on one primary algorithm.

The first algorithm will be selected during the architecture and implementation phase.

The system should later be capable of supporting algorithms such as:

- Token Bucket.
- Fixed Window.
- Sliding Window.
- Leaky Bucket.

The policy and enforcement layers should depend on a common algorithm contract rather than the internal implementation of a specific algorithm.

---

### 3.6 Traffic Decisions

Every request evaluation must produce a clear traffic-control decision.

A decision should indicate whether the request was allowed or rejected.

The decision model may contain information such as:

- Decision status.
- Applied policy identifier.
- Reason for rejection.
- Remaining capacity.
- Evaluation timestamp.

Conceptually, a decision may be represented as:

```text
ALLOW
```

or:

```text
REJECT

Reason: TRAFFIC_LIMIT_EXCEEDED
```

The decision model should remain extensible because distributed enforcement may later require additional information.

Possible future information includes:

- Policy version.
- Capacity allocation identifier.
- Lease identifier.
- Retry-after duration.
- Remaining allocated capacity.

---

### 3.7 Request Rejection

When a request exceeds the available traffic capacity, the system must reject the request.

The core traffic-control domain will determine the traffic decision.

The HTTP or gateway integration layer will translate the rejection decision into an appropriate response.

The traffic-control domain should not contain HTTP response-generation logic.

This separation will allow the same enforcement engine to be used in environments other than HTTP request processing.

---

## 4. Distributed Traffic-Control Scope

The primary long-term scope of the project is distributed traffic control.

A distributed deployment may contain multiple gateway nodes processing requests independently.

For example:

```text
                    +-----------+
Client Requests --->| Gateway A |
                    +-----------+

                    +-----------+
Client Requests --->| Gateway B |
                    +-----------+

                    +-----------+
Client Requests --->| Gateway C |
                    +-----------+
```

If each gateway independently maintains its own traffic-control state, a global traffic policy cannot be reliably enforced.

For example:

```text
Global Client Limit: 10,000 requests per minute

Gateway A Local Limit: 10,000 requests per minute
Gateway B Local Limit: 10,000 requests per minute
Gateway C Local Limit: 10,000 requests per minute
```

If the same client distributes requests across all three nodes, the client may consume significantly more than the intended global quota.

The distributed architecture will address this coordination problem.

---

## 5. Data Plane

The data plane will be responsible for processing incoming application traffic.

Gateway nodes will form the primary components of the data plane.

The data plane should eventually:

- Receive incoming requests.
- Identify the traffic subject.
- Resolve the applicable traffic policy.
- Evaluate locally available traffic capacity.
- Allow or reject requests.
- Minimize additional latency in the critical request path.

The data plane should not require a centralized coordination request for every incoming API request.

Local enforcement should remain the preferred execution path whenever valid local capacity is available.

---

## 6. Control Plane

The control plane will manage the shared coordination responsibilities required by the distributed platform.

The control plane will eventually manage capabilities such as:

- Traffic policy creation.
- Traffic policy updates.
- Policy versioning.
- Policy distribution.
- Gateway registration.
- Gateway membership awareness.
- Global capacity coordination.
- Capacity allocation.
- Capacity recovery.

The control plane should not process normal application requests directly.

Its responsibility is to coordinate and distribute the information required by data-plane nodes.

This separation allows request processing and distributed coordination to evolve independently.

---

## 7. Capacity Allocation

The distributed platform will eventually support allocating portions of global traffic capacity to individual gateway nodes.

For example:

```text
Global Quota: 10,000 requests per minute

Gateway A Allocation: 3,000
Gateway B Allocation: 3,000
Gateway C Allocation: 4,000
```

Each gateway can then evaluate requests locally against its allocated capacity.

The gateway does not need to contact the central coordination component for every request.

When additional capacity is required, or when existing capacity expires, the gateway can coordinate with the control plane.

This approach reduces centralized coordination in the critical request path.

The initial implementation will not include distributed capacity allocation.

The initial domain model should, however, avoid assumptions that would prevent capacity allocation from being introduced later.

Future allocation strategies may include:

- Static allocation.
- Equal allocation.
- Weighted allocation.
- Demand-based allocation.
- Adaptive allocation.

---

## 8. Policy Propagation

The distributed platform will eventually support propagating policy updates to gateway nodes.

Policies may change while nodes are actively processing traffic.

For example:

```text
Policy Version 1
       |
       v
Gateway Nodes Enforce Version 1
       |
       v
Policy Updated
       |
       v
Policy Version 2
       |
       v
Updated Policy Propagated to Gateway Nodes
```

The distributed system will eventually need to handle:

- Policy versions.
- Stale policies.
- Delayed updates.
- Update ordering.
- Gateway synchronization.
- Safe policy replacement.

The initial implementation may store policies locally.

However, policy versioning should be considered from the beginning because it will become important when policies are distributed between nodes.

---

## 9. Failure Handling

The distributed platform must eventually account for partial failures.

A gateway node may:

- Stop unexpectedly.
- Restart.
- Lose network connectivity.
- Become temporarily unavailable.
- Continue operating with stale coordination information.

The control plane may also become temporarily unavailable.

The future distributed implementation must define how traffic enforcement behaves during these situations.

Important questions include:

- What happens to capacity allocated to a failed gateway?
- When can unused capacity be recovered?
- How long may a gateway continue using existing allocations?
- What happens when a gateway cannot contact the control plane?
- How should stale policies be handled?
- How should expired allocations be handled?

These scenarios are not part of the initial local enforcement implementation.

They are part of the long-term distributed-system scope and will be addressed in later phases.

---

## 10. Observability

The platform should eventually provide visibility into traffic-control behavior and distributed coordination.

Important metrics may include:

- Total requests evaluated.
- Requests allowed.
- Requests rejected.
- Requests evaluated per policy.
- Requests evaluated per traffic subject.
- Available local capacity.
- Capacity allocation per gateway.
- Allocation renewal failures.
- Policy propagation status.
- Policy version mismatches.
- Gateway coordination failures.

The platform should also support structured logging for important events.

Examples include:

- Policy creation.
- Policy updates.
- Policy propagation failures.
- Gateway registration.
- Gateway failures.
- Capacity allocation.
- Capacity expiration.
- Repeated traffic rejection.

Observability will be introduced progressively.

The core traffic-control domain should not be tightly coupled to a specific monitoring implementation.

---

## 11. Initial API Scope

The initial implementation will expose only the APIs required to demonstrate and validate the traffic-control functionality.

The API surface will remain intentionally small.

Initial policy operations may include:

```text
POST /api/policies
GET  /api/policies
GET  /api/policies/{policyId}
```

A protected demonstration endpoint may also be introduced.

For example:

```text
GET /api/demo/resource
```

Requests to the protected endpoint should pass through the traffic-control enforcement flow.

The exact endpoint design may evolve during implementation.

The public API should not expose unnecessary internal details of the traffic-control algorithm.

---

## 12. Initial Release Scope

The first implementation milestone will include:

- Spring Boot project foundation.
- Core traffic-control domain model.
- Traffic subject abstraction.
- Traffic policy model.
- Policy resolution abstraction.
- One traffic-control algorithm.
- Local traffic state.
- Request evaluation.
- Allow or reject decisions.
- Basic HTTP integration.
- Unit tests for enforcement logic.
- Basic integration tests.

The initial release will focus on correctness, clean boundaries, and extensibility.

Distributed coordination will not be implemented in the first milestone.

---

## 13. Future Scope

The following capabilities are planned for later phases.

### 13.1 Distributed Gateway Deployment

- Multiple gateway instances.
- Shared traffic policies.
- Load-balanced request processing.
- Demonstration of the global quota problem.

### 13.2 Control Plane

- Centralized policy management.
- Gateway registration.
- Gateway membership awareness.
- Coordination APIs.

### 13.3 Distributed Capacity Allocation

- Global quota management.
- Gateway capacity allocation.
- Capacity leases.
- Allocation renewal.
- Allocation expiration.
- Capacity recovery.

### 13.4 Policy Distribution

- Versioned policies.
- Policy propagation.
- Stale policy detection.
- Gateway synchronization.

### 13.5 Failure Recovery

- Gateway failure detection.
- Lease expiration.
- Capacity reclamation.
- Control-plane failure handling.
- Network partition behavior.
- Recovery procedures.

### 13.6 Adaptive Traffic Control

- Demand-aware allocation.
- Dynamic quota redistribution.
- Gateway load awareness.
- Hot-client detection.

### 13.7 Deployment and Testing

- Docker-based local deployment.
- Multiple gateway replicas.
- Container orchestration.
- Kubernetes deployment.
- Load testing.
- Failure simulation.

---

## 14. Out of Scope

The following capabilities are explicitly outside the initial product scope:

- Complete API gateway functionality.
- API monetization.
- Billing systems.
- Subscription management.
- Authentication provider implementation.
- Web application firewall functionality.
- DDoS protection.
- Full API management capabilities.
- Service mesh implementation.
- Multi-region traffic coordination.
- Production user interface or dashboard.

The platform may integrate with some of these systems in the future, but they are not the purpose of this project.

---

## 15. Product Boundaries

Distributed Traffic Control is a specialized traffic-policy enforcement and coordination platform.

It is not intended to become a complete API gateway.

It is not intended to replace existing API management platforms.

It is not intended to provide complete application security.

The primary technical focus of the project is:

- Local traffic enforcement.
- Distributed coordination.
- Global quota management.
- Capacity allocation.
- Policy propagation.
- Consistency.
- Failure handling.
- Scalability.
- Observability.

These boundaries are important to prevent unnecessary expansion of the project.

---

## 16. Product Evolution

The product will evolve in progressive stages.

### Stage 1: Local Traffic Control

Implement local traffic-policy evaluation and local capacity enforcement.

The objective is to establish a correct and independently testable traffic-control domain.

### Stage 2: Request-Path Integration

Integrate the traffic-control engine into a realistic request-processing flow.

The objective is to demonstrate how traffic decisions affect incoming API requests.

### Stage 3: Distributed Gateway Deployment

Introduce multiple gateway instances.

The objective is to reproduce the coordination problem created by independently enforced local limits.

### Stage 4: Control Plane

Introduce a dedicated control-plane component.

The objective is to separate traffic coordination from request processing.

### Stage 5: Distributed Capacity Allocation

Introduce globally coordinated capacity allocations.

The objective is to allow gateway nodes to enforce traffic locally while respecting a shared global quota.

### Stage 6: Failure Handling

Introduce allocation expiration, recovery, stale-state handling, and gateway failure scenarios.

The objective is to make the distributed architecture resilient to partial failures.

### Stage 7: Adaptive Allocation

Introduce dynamic capacity redistribution based on observed traffic demand.

The objective is to improve capacity utilization while preserving traffic-control guarantees.

---

## 17. Scope Summary

The initial product will establish a clean foundation for local traffic-control enforcement.

It will support traffic policies, traffic subject identification, policy resolution, algorithm abstraction, local capacity evaluation, and request decisions.

The platform will then evolve into a distributed traffic-control system where multiple gateway nodes enforce globally defined traffic policies through coordinated capacity allocation and locally executed decisions.

The guiding architectural principle of the project is:

> Keep request-path enforcement local whenever possible and move distributed coordination outside the critical request path.

This principle will guide the evolution of the project from a local traffic-control engine into a distributed control-plane and data-plane architecture.