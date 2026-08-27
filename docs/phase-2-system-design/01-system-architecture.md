# System Architecture

## 1. Purpose

This document defines the high-level architecture of the API Traffic Control Platform.

The platform is designed as a distributed traffic-control system that enforces global and hierarchical API quotas across multiple gateway nodes without requiring every incoming request to synchronously contact a central coordinator.

The central architectural idea is to separate the system into two logical planes:

- **Control Plane** — responsible for policy management, global coordination, capacity allocation, policy propagation, and system state.
- **Data Plane** — responsible for processing API traffic and enforcing quotas locally at gateway nodes.

This separation allows the request path to remain fast and available while still enabling globally coordinated quota enforcement.

The architecture is intentionally designed to avoid unnecessary microservice decomposition. The initial implementation may run as a modular Spring Boot application with clearly separated modules and responsibilities. Logical boundaries can later be deployed independently if operational requirements justify doing so.

---

# 2. Architectural Goals

The architecture must support the following goals.

## 2.1 Low-Latency Request Enforcement

The allow or reject decision for an incoming API request should normally happen locally on the gateway node.

A gateway should not need to contact the Control Plane for every request.

## 2.2 Global Quota Coordination

Multiple gateway nodes must collectively enforce a shared global quota.

For example, if a tenant has a global limit of:

```text
100,000 requests per minute
```

the combined traffic processed by all gateways must respect that limit.

## 2.3 Hierarchical Quotas

A single request may be subject to multiple quota levels.

For example:

```text
Global Platform
        │
        ▼
Tenant
        │
        ▼
Application
        │
        ▼
API / Route
        │
        ▼
Consumer
```

The request is allowed only when the applicable quota constraints permit it.

## 2.4 Local Enforcement During Normal Operation

Gateway nodes receive capacity in advance through token leases and enforce that capacity locally.

This reduces coordination overhead and avoids adding Control Plane latency to the request path.

## 2.5 Failure Awareness

The system must define behavior for failures such as:

- Control Plane unavailable
- Gateway node unavailable
- Lease renewal failure
- Policy propagation delay
- Network partition
- Stale local state

The system should degrade in a controlled and explicit manner.

## 2.6 Evolvable Architecture

The initial implementation should be practical for development while maintaining clean boundaries for future scaling.

The project should not create separate services simply to appear distributed.

---

# 3. High-Level Architecture

The system consists of three primary logical areas.

```text
                         ┌──────────────────────────┐
                         │      Administrators      │
                         │   Policy / Configuration │
                         └────────────┬─────────────┘
                                      │
                                      ▼
                    ┌──────────────────────────────────┐
                    │          CONTROL PLANE           │
                    │                                  │
                    │  Policy Management               │
                    │  Policy Versioning               │
                    │  Capacity Allocation             │
                    │  Token Lease Coordination        │
                    │  Gateway Coordination            │
                    │  Policy Distribution             │
                    │  Failure Coordination            │
                    └───────────────┬──────────────────┘
                                    │
                         Policies / Leases / Versions
                                    │
                ┌───────────────────┼───────────────────┐
                │                   │                   │
                ▼                   ▼                   ▼
        ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
        │ Gateway Node │    │ Gateway Node │    │ Gateway Node │
        │      A       │    │      B       │    │      C       │
        │              │    │              │    │              │
        │ Local Policy │    │ Local Policy │    │ Local Policy │
        │ Local Lease  │    │ Local Lease  │    │ Local Lease  │
        │ Local Tokens │    │ Local Tokens │    │ Local Tokens │
        └──────┬───────┘    └──────┬───────┘    └──────┬───────┘
               │                   │                   │
               ▼                   ▼                   ▼
          API Traffic         API Traffic         API Traffic
```

The Control Plane does not sit directly in the synchronous request path.

Gateway nodes continue enforcing traffic locally using the policies and leased capacity currently available to them.

---

# 4. Control Plane

The Control Plane manages the global state required to coordinate distributed quota enforcement.

It is responsible for deciding **what rules exist** and **how global capacity is distributed**.

The Control Plane is not responsible for making every individual allow/reject decision.

## 4.1 Control Plane Responsibilities

The Control Plane contains the following logical modules.

### Policy Management

Responsible for:

- Creating quota policies
- Updating policies
- Enabling and disabling policies
- Associating policies with hierarchy scopes
- Validating policy configuration

### Policy Versioning

Responsible for:

- Assigning versions to policy changes
- Tracking the current policy version
- Allowing gateways to identify stale policy state
- Supporting ordered policy updates

### Capacity Allocation

Responsible for:

- Tracking globally available quota capacity
- Allocating capacity to gateway nodes
- Adjusting allocations based on demand
- Preventing excessive allocation beyond configured limits

### Token Lease Coordination

Responsible for:

- Issuing token leases
- Renewing leases
- Tracking lease expiry
- Preventing duplicate or invalid lease usage
- Maintaining lease ownership information

### Gateway Coordination

Responsible for:

- Tracking active gateway nodes
- Recording node metadata
- Recording gateway heartbeats
- Detecting inactive nodes

### Policy Distribution

Responsible for propagating policy changes to gateway nodes.

The first implementation may support pull-based synchronization. Future versions may add push-based distribution.

### Failure Coordination

Responsible for maintaining enough global state to support recovery and reconciliation after distributed failures.

---

# 5. Data Plane

The Data Plane processes incoming API traffic.

Each gateway node contains the local state required to make traffic-control decisions without contacting the Control Plane for every request.

## 5.1 Gateway Responsibilities

A gateway node is responsible for:

- Receiving incoming API requests
- Identifying the applicable traffic-control scope
- Resolving the applicable policies
- Checking locally available token leases
- Enforcing hierarchical quotas
- Allowing or rejecting requests
- Recording local consumption
- Requesting additional capacity when required
- Synchronizing policies and leases with the Control Plane

## 5.2 Local Gateway State

Each gateway maintains local state such as:

```text
Gateway Node
│
├── Policy Cache
│     ├── Policy Definitions
│     └── Policy Versions
│
├── Lease Store
│     ├── Lease ID
│     ├── Scope
│     ├── Allocated Tokens
│     └── Expiry
│
├── Token Counters
│     └── Remaining Local Capacity
│
└── Gateway Metadata
      ├── Node ID
      └── Last Synchronization State
```

The exact storage implementation may initially use in-memory structures.

A later implementation may introduce durable or shared storage where required by the deployment model.

---

# 6. Control Plane and Data Plane Interaction

The interaction between the two planes should be asynchronous relative to normal request processing.

The primary communication flows are:

```text
Gateway Node
    │
    ├── Register Node ───────────────► Control Plane
    │
    ├── Fetch Policy Updates ────────► Control Plane
    │
    ├── Request Token Lease ─────────► Control Plane
    │
    ├── Renew Token Lease ───────────► Control Plane
    │
    └── Send Node State / Heartbeat ─► Control Plane
```

The Control Plane responds with:

```text
Control Plane
    │
    ├── Policy Definitions
    ├── Policy Versions
    ├── Token Lease Grants
    ├── Lease Renewal Results
    └── Coordination State
```

The gateway uses this information to continue enforcing requests locally.

---

# 7. Request Processing Flow

A normal API request follows this high-level flow.

```text
Client
  │
  │ HTTP Request
  ▼
Gateway Node
  │
  ├── Identify Tenant / Application / API / Consumer
  │
  ▼
Resolve Applicable Policies
  │
  ▼
Check Local Policy Version
  │
  ▼
Evaluate Hierarchical Quotas
  │
  ├── Global Scope
  ├── Tenant Scope
  ├── Application Scope
  ├── API Scope
  └── Consumer Scope
  │
  ▼
Check Local Token Availability
  │
  ├── Capacity Available ──► Allow Request
  │
  └── Capacity Unavailable ─► Reject or Request Additional Lease
```

Under normal operation, the request should complete without a synchronous Control Plane request.

---

# 8. Hierarchical Enforcement Model

Quota policies can exist at multiple levels.

Consider the following example:

```text
Global Platform Limit      = 1,000,000 requests/minute
Tenant Limit               =   100,000 requests/minute
Application Limit          =    50,000 requests/minute
API Route Limit            =     5,000 requests/minute
Consumer Limit             =     1,000 requests/minute
```

A request from a consumer may require capacity from several applicable scopes.

Conceptually:

```text
Global quota       ✓
Tenant quota       ✓
Application quota  ✓
API quota          ✓
Consumer quota     ✓
        │
        ▼
   REQUEST ALLOWED
```

If a required scope has no available capacity:

```text
Global quota       ✓
Tenant quota       ✗
        │
        ▼
   REQUEST REJECTED
```

The detailed algorithm for hierarchical reservation and rollback is defined in the distributed design document.

---

# 9. Token Lease Architecture

Global quota coordination is achieved through locally enforced token leases.

Instead of asking the Control Plane for permission for every request, a gateway receives a lease containing a limited amount of capacity.

Example:

```text
Tenant Global Capacity: 100,000 tokens

Control Plane Allocation:

Gateway A → 30,000 tokens
Gateway B → 25,000 tokens
Gateway C → 20,000 tokens

Unallocated / Reserved Capacity → 25,000 tokens
```

Gateway A can process up to 30,000 applicable requests using its local lease without coordinating for each request.

A conceptual lease contains:

```text
Lease ID
Gateway Node ID
Quota Scope
Allocated Token Count
Remaining Token Count
Lease Expiry
Policy Version
```

Once capacity is consumed or the lease approaches expiry, the gateway communicates with the Control Plane to obtain additional capacity or renew its lease.

This mechanism moves global coordination away from the critical request path.

---

# 10. Policy Distribution Architecture

Policies are centrally managed but locally cached.

Each policy has a version.

Example:

```text
Tenant Policy
Version 10
```

After an administrator changes the policy:

```text
Tenant Policy
Version 11
```

Gateway nodes compare their local version with the latest Control Plane version.

```text
Gateway A
Local Version = 10

Control Plane
Current Version = 11

Result:
Policy synchronization required
```

The gateway then updates its local policy state.

The architecture must ensure that policy updates and leases remain compatible.

A lease associated with an outdated policy version may require invalidation, reconciliation, or controlled expiry depending on the update type.

The exact consistency and propagation behavior is defined in the distributed design.

---

# 11. Capacity Allocation Architecture

The Control Plane manages the distribution of global quota capacity across gateway nodes.

A simple equal distribution may waste capacity when traffic is uneven.

For example:

```text
Gateway A → High Traffic
Gateway B → Medium Traffic
Gateway C → Low Traffic
```

A static allocation such as:

```text
33% / 33% / 33%
```

may cause Gateway A to exhaust its capacity while Gateway C still has unused tokens.

Therefore, the architecture supports adaptive capacity allocation.

The Control Plane can consider information such as:

- Recent request rate
- Lease consumption rate
- Number of active nodes
- Remaining global capacity
- Recent lease renewal frequency

The first implementation may use a simple allocation strategy.

The architecture should keep allocation as a separate module so more advanced algorithms can be introduced later.

---

# 12. Storage Architecture

The platform requires persistent state for Control Plane data.

The initial design separates state into the following conceptual categories.

## 12.1 Persistent Configuration State

Examples:

- Quota policies
- Policy hierarchy
- Policy versions
- Administrative configuration

This state should survive Control Plane restart.

## 12.2 Coordination State

Examples:

- Active leases
- Gateway registrations
- Lease ownership
- Lease expiry metadata

The implementation may choose a database, distributed cache, or other coordination mechanism based on consistency and performance requirements.

## 12.3 Local Gateway State

Examples:

- Cached policies
- Local leases
- Local token counters
- Recent synchronization state

This state should be available with low latency.

The initial implementation can begin with local in-memory state while clearly isolating the storage abstraction.

---

# 13. Initial Implementation Topology

The first implementation does not need multiple independently deployed Spring Boot services.

A recommended initial topology is:

```text
                    ┌───────────────────────────────┐
                    │      Spring Boot Application  │
                    │                               │
                    │  ┌─────────────────────────┐  │
                    │  │     Control Plane       │  │
                    │  │                         │  │
                    │  │ Policy Management       │  │
                    │  │ Lease Coordination      │  │
                    │  │ Capacity Allocation     │  │
                    │  │ Policy Distribution     │  │
                    │  └─────────────────────────┘  │
                    │                               │
                    │  ┌─────────────────────────┐  │
                    │  │      Data Plane         │  │
                    │  │                         │  │
                    │  │ Request Enforcement     │  │
                    │  │ Local Token Store       │  │
                    │  │ Policy Cache            │  │
                    │  └─────────────────────────┘  │
                    └───────────────────────────────┘
```

The codebase should use modular package boundaries even when deployed as one application.

A future distributed deployment can run multiple gateway/data-plane instances and one or more Control Plane instances.

---

# 14. Recommended Logical Module Boundaries

The application should eventually be organized around logical responsibilities rather than arbitrary layers.

A conceptual structure is:

```text
com.yash.apitrafficcontrol
│
├── policy
│   ├── domain
│   ├── application
│   └── infrastructure
│
├── lease
│   ├── domain
│   ├── application
│   └── infrastructure
│
├── quota
│   ├── domain
│   └── application
│
├── gateway
│   ├── enforcement
│   ├── cache
│   └── synchronization
│
├── allocation
│
├── coordination
│
└── shared
```

The exact package names can change during implementation.

The important requirement is that policy management, lease coordination, allocation, and request enforcement remain conceptually separated.

---

# 15. Deployment Evolution

The architecture can evolve through stages.

## Stage 1 — Single Process Development

```text
One Spring Boot Application
        │
        ├── Control Plane Modules
        └── Data Plane Modules
```

Purpose:

- Validate domain model
- Implement policy management
- Implement lease protocol
- Test quota enforcement logic

## Stage 2 — Multiple Gateway Instances

```text
              Control Plane
                    │
         ┌──────────┼──────────┐
         ▼          ▼          ▼
      Gateway A  Gateway B  Gateway C
```

Purpose:

- Validate distributed token leases
- Validate global quota coordination
- Test uneven traffic distribution

## Stage 3 — Scaled Control Plane

```text
          ┌──────────────────┐
          │ Load Balancer    │
          └────────┬─────────┘
                   │
        ┌──────────┼──────────┐
        ▼          ▼          ▼
   Control A   Control B   Control C
        │          │          │
        └──────────┼──────────┘
                   │
             Shared State
```

Purpose:

- High availability
- Control Plane scaling
- Failure recovery
- Coordination consistency

The project should reach these stages only when the underlying behavior is implemented and tested.

---

# 16. Observability Architecture

The architecture should expose operational visibility for both planes.

Important metrics include:

## Gateway Metrics

- Requests allowed
- Requests rejected
- Local token consumption
- Lease acquisition latency
- Lease renewal failures
- Policy synchronization lag

## Control Plane Metrics

- Active gateway nodes
- Active leases
- Remaining global capacity
- Lease allocation rate
- Policy propagation latency
- Allocation failures

## Distributed-System Metrics

- Lease expiry events
- Stale policy events
- Gateway heartbeat failures
- Reconciliation events
- Fail-open or fail-closed decisions

Observability should be designed into the domain flows rather than added only after implementation is complete.

---

# 17. Security Boundaries

Control Plane and Data Plane communication must eventually be authenticated.

Important boundaries include:

```text
Administrator
      │
      ▼
Control Plane API
      │
      ▼
Gateway Coordination API
      │
      ▼
Gateway Nodes
```

Gateway nodes must be identifiable when requesting leases or reporting state.

A gateway must not be able to impersonate another gateway and consume its allocated lease.

The initial implementation may use a simplified authentication mechanism while keeping the communication interfaces ready for stronger service-to-service authentication.

---

# 18. Architecture Trade-Offs

## Local Enforcement vs Perfect Global Precision

Local token leases reduce request latency and improve availability.

However, distributing capacity in advance introduces the possibility of temporarily unused capacity being held by one gateway while another gateway needs it.

The architecture accepts bounded allocation inefficiency in exchange for removing global coordination from every request.

## Policy Propagation vs Immediate Consistency

Immediate synchronous policy updates across all gateways would increase coordination complexity.

Versioned asynchronous propagation provides better availability and scalability.

The system must therefore explicitly define how stale policy versions are handled.

## Simple Deployment vs Logical Separation

The initial implementation uses modular boundaries instead of immediately creating multiple services.

This reduces operational complexity while preserving the ability to deploy components separately later.

---

# 19. Architecture Summary

The API Traffic Control Platform uses a control-plane/data-plane architecture.

The Control Plane manages:

```text
Policies
Versions
Global Capacity
Token Leases
Gateway Coordination
Capacity Allocation
```

Gateway nodes manage:

```text
Incoming API Traffic
Local Policy Cache
Locally Leased Tokens
Hierarchical Enforcement
Local Allow / Reject Decisions
```

The primary architectural principle is:

> Global coordination happens before or outside the normal request path, while traffic enforcement happens locally at gateway nodes.

Token leases provide the bridge between globally coordinated quotas and low-latency local enforcement.

The next design document defines the distributed mechanisms behind this architecture, including lease semantics, consistency behavior, hierarchical quota coordination, adaptive allocation, failure handling, and recovery.
