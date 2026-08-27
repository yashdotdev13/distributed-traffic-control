# Functional Requirements

## 1. Purpose

This document defines the functional requirements for the Distributed Traffic Control Platform.

The platform is intended to control API traffic across multiple gateway nodes while supporting centralized policy management and distributed traffic enforcement.

The system must allow traffic policies to be defined centrally and enforced at gateway nodes.

The initial implementation will establish the functional foundation required for later distributed coordination.

The platform will eventually support:

- Global API quotas
- Hierarchical traffic limits
- Distributed gateway enforcement
- Locally enforced token leases
- Adaptive capacity allocation
- Versioned policy propagation
- Failure-aware coordination

The first implementation does not need to deliver every advanced distributed capability immediately.

However, the functional design must not prevent those capabilities from being introduced later.

---

# 2. Functional Overview

The platform will manage the lifecycle of API traffic policies and evaluate incoming requests against those policies.

The system must support:

- Traffic policy management
- Traffic subject identification
- Policy resolution
- Request traffic evaluation
- Local gateway enforcement
- Policy versioning
- Future distributed capacity allocation

---

# 3. Traffic Policy Management

## 3.1 Create Traffic Policy

The system must allow an administrator to create a traffic-control policy.

A traffic policy defines how API traffic should be controlled for a particular traffic subject.

A policy must contain enough information to determine:

- The identity or scope of the policy
- The traffic-control limit
- The enforcement algorithm
- The policy status
- The policy version

A policy may conceptually contain:

- policyId
- subject
- quota
- algorithm
- status
- version
- createdAt
- updatedAt

The exact persistence schema does not need to be finalized during the product requirements phase.

However, the domain model must support policy evolution.

---

## 3.2 Retrieve Traffic Policy

The system must allow an administrator or authorized internal component to retrieve a traffic policy.

Policies must be retrievable using their unique identifier.

The system should also support retrieving policies based on their traffic subject.

The policy returned by the system must include its current version.

---

## 3.3 Update Traffic Policy

The system must allow an existing traffic policy to be updated.

An update may modify configuration such as:

- Capacity
- Refill rate
- Algorithm
- Enforcement status
- Other supported traffic-control settings

When a policy is changed, the system must create a newer policy version.

Gateway nodes must eventually be able to determine that the policy has changed.

The system must avoid treating policy updates as invisible mutations.

Conceptually:

```text
Policy Version 1
       |
       v
Policy Update
       |
       v
Policy Version 2
       |
       v
Policy Distribution
       |
       v
Gateway Nodes Receive Updated Policy
```

---

## 3.4 Activate and Deactivate Traffic Policies

The system must support enabling and disabling traffic policies.

An active policy must participate in request evaluation.

An inactive policy must not actively reject traffic.

The system should maintain the policy configuration even when the policy is inactive.

A policy state change must also be propagated as a policy update.

---

## 3.5 Delete Traffic Policy

The system must support deleting a traffic policy.

The deletion behavior must ensure that gateway nodes do not continue enforcing an invalid policy indefinitely.

The distributed propagation mechanism will later define how policy removal is communicated across gateway nodes.

---

# 4. Traffic Subject Identification

## 4.1 Purpose

Before a request can be evaluated, the system must identify the traffic subject associated with that request.

The traffic subject represents the entity against which a traffic policy should be evaluated.

Examples may include:

- API client
- Authenticated user
- Tenant
- API key
- Application
- Service

The initial implementation should focus on a clearly defined primary subject type.

The domain model must not permanently restrict the system to only one subject type.

---

## 4.2 Subject Resolution

The gateway enforcement component must determine the traffic subject for every request that requires traffic control.

Conceptually:

```text
Incoming Request
       |
       v
Extract Request Context
       |
       v
Identify Traffic Subject
       |
       v
Resolve Applicable Policy
```

The mechanism used to identify the subject must remain separate from the traffic-control algorithm.

The enforcement engine should receive a normalized traffic subject.

---

# 5. Policy Resolution

## 5.1 Resolve Applicable Policy

After identifying the traffic subject, the system must determine which traffic policy applies to the request.

The initial implementation may support one primary policy lookup strategy.

The design must later support hierarchical policy resolution.

For example:

```text
Request
   |
   v
Client Policy
   |
   v
Tenant Policy
   |
   v
Global Policy
```

The exact hierarchy does not need to be fully implemented initially.

However, policy resolution must not be tightly designed around the assumption that exactly one policy will always exist.

---

## 5.2 No Applicable Policy

The system must define behavior for requests that do not match an active traffic policy.

Possible behaviors include:

```text
No Policy Found
      |
      +-------------------+
      |                   |
      v                   v
Allow Request        Apply Default Policy
```

The system must not produce unpredictable traffic-control behavior when policy resolution fails.

---

# 6. Request Traffic Evaluation

## 6.1 Evaluate Request

The gateway node must evaluate an incoming request against the applicable traffic policy.

The evaluation process must determine whether sufficient traffic capacity is available.

Conceptually:

```text
Incoming Request
       |
       v
Resolve Policy
       |
       v
Traffic Control Algorithm
       |
       v
Check Available Capacity
       |
       +-------------------+
       |                   |
       v                   v
    Allowed             Rejected
```

The enforcement decision must be produced before the protected request is forwarded to the downstream service.

---

## 6.2 Allow Request

When sufficient capacity is available, the traffic-control system must allow the request.

The request must then continue through the gateway.

The system must account for the capacity consumed by the allowed request.

---

## 6.3 Reject Request

When sufficient capacity is not available, the traffic-control system must reject the request.

The request must not be forwarded to the protected downstream service.

The rejection response should provide a consistent API-level indication that the request was denied because of traffic-control policy.

---

# 7. Traffic-Control Algorithm Abstraction

## 7.1 Algorithm Independence

The platform must define an abstraction for traffic-control algorithms.

The rest of the enforcement system should not be tightly coupled to a specific algorithm implementation.

Potential future algorithms may include:

- Token Bucket
- Fixed Window
- Sliding Window
- Leaky Bucket

The initial implementation should focus on Token Bucket.

---

## 7.2 Algorithm Evaluation Contract

The algorithm abstraction must provide a mechanism for evaluating whether a request can consume traffic capacity.

The evaluation result should contain enough information for the enforcement layer to make a request decision.

The domain should avoid coupling algorithm-specific state directly to HTTP framework components.

---

# 8. Local Gateway Enforcement

## 8.1 Local Decision Making

A gateway node must be able to make traffic-control decisions locally.

The long-term architecture must avoid requiring a centralized network call for every incoming request.

Conceptually:

```text
                Control Plane
                     |
             Policy / Capacity
                     |
        +------------+------------+
        |            |            |
        v            v            v
    Gateway A    Gateway B    Gateway C
        |            |            |
        v            v            v
   Local Decision Local Decision Local Decision
```

---

## 8.2 Gateway Independence

Each gateway node should maintain the state necessary to evaluate requests assigned to that node.

A gateway node should not require another gateway node to respond before processing each request.

The system must distinguish between:

- Control-plane coordination
- Data-plane enforcement

---

# 9. Hierarchical Traffic Control

The platform must eventually support multiple levels of traffic control.

A request may be subject to more than one quota.

For example:

```text
Global Quota
     |
     v
Tenant Quota
     |
     v
Client Quota
     |
     v
Request Decision
```

A request should only be allowed when all required applicable traffic constraints permit the request.

The initial implementation does not need to fully implement every hierarchy level.

However, the policy model and evaluation architecture must allow multiple constraints to be evaluated in the future.

---

# 10. Policy Propagation

## 10.1 Versioned Policies

Every traffic policy distributed to gateway nodes must have a version.

Gateway nodes must be able to determine whether their local policy representation is outdated.

Conceptually:

```text
Control Plane
Policy Version = 10
        |
        v
Gateway A -> Version 10
Gateway B -> Version 10
Gateway C -> Version 9
                    |
                    v
             Update Required
```

---

## 10.2 Policy Updates

When a traffic policy changes, the system must provide a mechanism through which gateway nodes can receive the updated policy.

Future implementations may introduce:

- Push-based updates
- Pull-based synchronization
- Event-driven propagation
- Version reconciliation

Gateway nodes must eventually converge toward the current active policy configuration.

---

# 11. Distributed Capacity Allocation

The platform must eventually enforce global quotas across multiple gateway nodes.

A global quota cannot be safely enforced by giving every gateway node an independent copy of the full quota.

The long-term functional model will use locally enforced token leases.

Conceptually:

```text
Global Capacity
      |
      v
Control Plane
      |
      +---------------------+
      |          |          |
      v          v          v
 Gateway A    Gateway B    Gateway C
 Lease: 3000  Lease: 4000  Lease: 3000
```

Each gateway can then consume its allocated capacity locally.

Requests should not require a centralized operation while locally leased capacity remains available.

---

# 12. Adaptive Capacity Allocation

The system must eventually support adaptive allocation of capacity across gateway nodes.

Traffic may not be distributed equally.

For example:

```text
Gateway A -> High Traffic
Gateway B -> Low Traffic
Gateway C -> Medium Traffic
```

A static equal allocation may result in one gateway exhausting capacity while another gateway holds unused capacity.

The control plane should eventually be able to consider traffic demand when allocating future leases.

The first implementation does not need to implement sophisticated adaptive algorithms.

---

# 13. Gateway Registration and Failure Awareness

The distributed system must eventually maintain awareness of participating gateway nodes.

A gateway node should be identifiable within the traffic-control system.

Future gateway information may include:

- Gateway identifier
- Gateway status
- Last communication time
- Current policy version
- Current lease information
- Traffic demand information

The platform must also eventually account for gateway failures, network isolation, deployment restarts, and application failures.

Failure and lease expiration semantics will be defined during distributed coordination design.

---

# 14. Request Decision Result

The enforcement system must produce a normalized traffic-control decision.

Conceptually:

```text
TrafficControlDecision
        |
        +-- ALLOWED
        |
        +-- REJECTED
```

The decision may eventually include:

- Policy identifier
- Policy version
- Rejection reason
- Remaining capacity
- Retry information

The domain decision object must remain independent from the HTTP transport layer.

---

# 15. Administrative Operations

The initial administrative capabilities should include:

- Create policy
- Retrieve policy
- Update policy
- Activate policy
- Deactivate policy
- Delete policy

Administrative operations are not part of the normal high-frequency request path.

---

# 16. Observability Requirements

The platform must expose sufficient functional information to understand traffic-control behavior.

The system should eventually provide visibility into:

- Allowed requests
- Rejected requests
- Policy evaluation failures
- Policy versions
- Gateway capacity
- Lease consumption
- Lease exhaustion
- Gateway health
- Policy propagation status

The exact monitoring technology is outside the scope of this document.

---

# 17. Initial Implementation Boundary

The initial implementation will focus on establishing the local traffic-control foundation.

The first functional implementation should include:

- Traffic subject identification
- Traffic policy model
- Policy management operations
- Policy resolution
- Token Bucket algorithm
- Local request evaluation
- Allow or reject decisions
- Gateway integration abstraction
- Policy versioning foundation

The following capabilities will be implemented in later phases:

- Multiple active gateway nodes
- Global quota coordination
- Token leases
- Lease reallocation
- Adaptive capacity allocation
- Hierarchical quota evaluation
- Distributed policy propagation
- Gateway heartbeats
- Failure-aware lease recovery
- Advanced consistency and reconciliation

---

# 18. Functional Constraints

The platform must not require a network call to a centralized coordinator for every request in its intended distributed architecture.

The request-time enforcement logic must remain separate from administrative policy management.

Traffic-control algorithms must remain replaceable.

Policy identity and traffic subject identity must be modeled explicitly.

Policy updates must support version tracking.

The domain model must not depend directly on a particular gateway framework.

The initial implementation should avoid introducing distributed coordination before the local enforcement model is stable and well-tested.

---

# 19. Summary

The Distributed Traffic Control Platform must provide a functional foundation for managing and enforcing API traffic policies.

The initial system will establish:

- A traffic policy domain
- Policy lifecycle management
- Traffic subject identification
- Policy resolution
- Algorithm abstraction
- Token Bucket enforcement
- Local allow or reject decisions
- Policy versioning

The architecture will later evolve toward distributed enforcement capabilities including:

- Multiple gateway nodes
- Hierarchical policies
- Global quotas
- Locally enforced token leases
- Adaptive capacity allocation
- Versioned policy propagation
- Gateway coordination
- Failure-aware recovery

The core architectural principle is that centralized coordination should manage policy and distributed capacity, while gateway nodes should make request-time traffic decisions locally whenever possible.

This separation provides the foundation for a distributed traffic-control platform that can enforce increasingly complex global traffic policies without placing centralized coordination directly in the critical path of every API request.
