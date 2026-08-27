# User Stories

## 1. Purpose

This document defines the primary user stories for the API Traffic Control Platform.

The platform has two major operational perspectives:

1. **Control-plane users and services** that create, manage, and coordinate traffic-control policies.
2. **Gateway nodes** that enforce those policies locally while participating in distributed quota coordination.

The stories below describe the expected behavior from the perspective of the main actors interacting with the system.

---

## 2. Actors

### Platform Administrator

A platform administrator manages traffic-control policies and monitors the overall state of the system.

Typical responsibilities include:

- creating and updating policies
- defining quota limits
- monitoring gateway participation
- investigating enforcement failures
- reviewing distributed coordination behavior

### API Consumer

An API consumer is an application, tenant, client, user, or API key whose requests are subject to traffic-control policies.

The consumer does not directly manage distributed coordination. Their requests are either allowed or rejected according to the active policy.

### Gateway Node

A gateway node is a data-plane runtime component responsible for enforcing traffic-control decisions for incoming requests.

A gateway node:

- maintains locally applicable policy state
- consumes locally available quota or token capacity
- requests additional capacity when necessary
- reports relevant usage information
- continues operating according to valid local state during temporary control-plane failures

### Control Plane

The control plane is responsible for authoritative policy management and distributed coordination.

It:

- stores policies
- versions policy changes
- tracks participating gateways
- allocates quota capacity
- issues or renews leases
- coordinates policy propagation

---

## 3. Policy Management Stories

### US-001: Create a Traffic-Control Policy

**As a platform administrator, I want to create a traffic-control policy so that requests can be controlled according to defined limits.**

#### Acceptance Criteria

- A policy has a unique identifier.
- A policy contains a defined traffic-control configuration.
- A newly created policy receives an initial version.
- The policy is persisted by the control plane.
- The policy can be associated with an applicable traffic dimension such as an API, tenant, client, or route.

---

### US-002: Update a Traffic-Control Policy

**As a platform administrator, I want to update an existing policy so that traffic limits can be changed without manually reconfiguring every gateway.**

#### Acceptance Criteria

- Updating a policy creates a newer policy version.
- The previous version is not accidentally treated as the latest version.
- Gateway nodes can determine whether they are enforcing an outdated policy.
- Updated policies are propagated to participating gateways.
- A gateway must not allow an older policy update to overwrite a newer policy.

---

### US-003: Disable a Policy

**As a platform administrator, I want to disable a traffic-control policy so that enforcement can be stopped in a controlled manner.**

#### Acceptance Criteria

- The disabled state is explicitly represented by the policy.
- The policy change is versioned.
- Gateway nodes receive the updated state.
- The system records the transition for operational investigation.

---

## 4. Request Enforcement Stories

### US-004: Enforce a Request Locally

**As a gateway node, I want to evaluate an incoming request using locally available policy and capacity so that traffic decisions can be made without contacting the control plane for every request.**

#### Acceptance Criteria

- The gateway identifies the applicable policy.
- The gateway checks locally available capacity or tokens.
- The gateway allows the request when sufficient valid capacity exists.
- The gateway rejects the request when the applicable limit has been reached.
- The decision path does not require synchronous control-plane communication under normal conditions.

---

### US-005: Reject Excess Traffic

**As an API consumer, I want to receive a clear rejection when I exceed an applicable traffic limit so that I can understand that the request was not accepted.**

#### Acceptance Criteria

- Rejected requests receive an appropriate rate-limiting response.
- The rejection can be associated with the applicable policy or reason internally.
- The rejection is reflected in operational metrics.
- The system does not silently drop the request without recording the decision.

---

### US-006: Apply Hierarchical Limits

**As a platform administrator, I want traffic policies to support multiple levels of control so that one consumer cannot bypass broader system limits.**

Examples may include:

- global limits
- tenant limits
- client limits
- API limits
- route limits

#### Acceptance Criteria

- A request can be evaluated against more than one applicable limit.
- The enforcement decision reflects the configured hierarchy.
- Exhaustion of one applicable limit can prevent the request even when another limit still has capacity.
- The hierarchy is explicit rather than dependent on gateway-specific assumptions.

---

## 5. Distributed Capacity Coordination Stories

### US-007: Allocate Local Capacity to a Gateway

**As the control plane, I want to allocate a bounded amount of quota capacity to a gateway so that the gateway can enforce requests locally without requiring central coordination for every request.**

#### Acceptance Criteria

- Allocated capacity is associated with a policy.
- The allocation is associated with a gateway.
- The allocation has a defined validity period or lease.
- The control plane can account for allocated capacity.
- A gateway cannot use capacity indefinitely after its lease expires.

---

### US-008: Request Additional Capacity

**As a gateway node, I want to request additional capacity when my locally allocated capacity becomes low or exhausted so that I can continue serving allowed traffic.**

#### Acceptance Criteria

- The gateway can detect low or exhausted local capacity.
- The request identifies the relevant policy and gateway.
- The control plane returns a bounded allocation or rejects the request.
- Retries do not create uncontrolled duplicate allocations.
- Allocation failures are observable.

---

### US-009: Renew a Valid Lease

**As a gateway node, I want to renew or replace my capacity lease before it expires so that locally authorized traffic can continue without interruption.**

#### Acceptance Criteria

- Lease validity can be determined by the gateway.
- Renewal is initiated before or when additional capacity is required.
- A successful renewal updates the locally valid allocation.
- An expired lease is not treated as indefinitely valid.
- Renewal failures are recorded.

---

### US-010: Bound Global Capacity Usage

**As the control plane, I want capacity distributed across gateways in bounded allocations so that independently enforced requests remain coordinated with the configured global quota.**

#### Acceptance Criteria

- The control plane does not allocate unlimited capacity to gateways.
- Capacity allocations can be accounted for by policy.
- The system can measure allocated and consumed capacity.
- Any possible global overshoot is bounded by the allocation strategy.
- The trade-off between strict global coordination and local performance is documented.

---

## 6. Gateway Lifecycle Stories

### US-011: Register a Gateway

**As a gateway node, I want to register with the control plane so that the platform knows that I am participating in traffic enforcement.**

#### Acceptance Criteria

- A gateway has a unique identifier.
- Registration records relevant gateway metadata.
- The control plane can track active or recently active gateways.
- Registration is safe to retry.
- Gateway registration state is observable.

---

### US-012: Detect an Unavailable Gateway

**As the control plane, I want to detect when a gateway has stopped communicating so that stale coordination state can eventually be handled safely.**

#### Acceptance Criteria

- Gateway activity has an observable freshness mechanism.
- The control plane can distinguish active and stale gateway state.
- Temporary communication failure does not immediately cause unsafe state reclamation.
- Stale allocations are handled according to lease expiry and recovery rules.

---

### US-013: Add a New Gateway Node

**As a platform operator, I want to add another gateway node so that request traffic can scale horizontally.**

#### Acceptance Criteria

- The new gateway can register without manual changes to existing gateway instances.
- The gateway receives applicable policy state.
- The gateway can participate in capacity allocation.
- Existing gateways continue operating during the addition of the new gateway.

---

## 7. Failure Handling Stories

### US-014: Continue Local Enforcement During a Control-Plane Outage

**As a gateway node, I want to continue enforcing requests using valid local state when the control plane becomes temporarily unavailable so that a central outage does not immediately stop all traffic processing.**

#### Acceptance Criteria

- The gateway can continue using a valid local policy.
- The gateway can continue consuming an unexpired local allocation.
- The gateway does not assume unlimited capacity during the outage.
- The gateway exposes the degraded state through metrics or logs.

---

### US-015: Stop Using Expired Authority

**As the control plane, I want gateway authority to expire when it is no longer valid so that disconnected nodes cannot consume capacity forever.**

#### Acceptance Criteria

- Every lease has an explicit expiry condition.
- The gateway can determine when its authority has expired.
- Expired capacity is not used for new decisions.
- The behavior after expiration is explicit and configurable according to the system design.

---

### US-016: Recover After Reconnection

**As a gateway node, I want to safely synchronize with the control plane after a temporary disconnection so that my policy and capacity state can converge with the authoritative system state.**

#### Acceptance Criteria

- The gateway can identify missed policy versions.
- Stale state is replaced safely.
- Duplicate reports or retries do not corrupt state.
- Expired allocations are not restored as valid merely because connectivity returns.
- Recovery events are observable.

---

## 8. Policy Propagation Stories

### US-017: Propagate a New Policy Version

**As the control plane, I want to distribute a newer policy version to gateway nodes so that enforcement behavior eventually reflects the latest configuration.**

#### Acceptance Criteria

- Every propagated update includes a policy version.
- Gateways can compare incoming and locally stored versions.
- A newer version replaces an older valid version.
- An older version cannot overwrite a newer version.
- Propagation failures can be detected.

---

### US-018: Identify Policy Propagation Lag

**As a platform administrator, I want to know which gateways are behind the latest policy version so that delayed propagation can be investigated.**

#### Acceptance Criteria

- The latest control-plane version is known.
- Gateway enforcement versions are observable.
- Version mismatches can be identified.
- Policy propagation lag is measurable.

---

## 9. Observability Stories

### US-019: Monitor Traffic Decisions

**As a platform administrator, I want to observe allowed and rejected traffic so that I can understand how policies are affecting API traffic.**

#### Acceptance Criteria

- Allowed decisions are measurable.
- Rejected decisions are measurable.
- Metrics can be grouped by relevant policy dimensions where practical.
- Decision latency is observable.

---

### US-020: Investigate a Distributed Coordination Failure

**As an engineer, I want sufficient logs, metrics, and tracing context to investigate failures involving gateways, policies, and capacity leases.**

#### Acceptance Criteria

- Failures include meaningful contextual information.
- Relevant operations can be correlated using identifiers.
- Lease and policy operations are traceable where distributed calls are involved.
- Repeated failures can be detected operationally.

---

### US-021: View Gateway State

**As a platform administrator, I want to inspect gateway participation and state so that I can identify unhealthy, disconnected, or outdated nodes.**

#### Acceptance Criteria

- Gateway identity is visible.
- Recent communication state is visible.
- Current policy version information is available.
- Relevant lease or allocation state can be inspected.
- Stale gateways can be identified.

---

## 10. Operational Stories

### US-022: Run Multiple Gateways Locally

**As a developer, I want to run multiple gateway instances locally so that I can reproduce distributed quota coordination behavior during development.**

#### Acceptance Criteria

- Multiple gateway instances can run simultaneously.
- Each instance has a distinct gateway identity.
- Each gateway can connect to the same control plane.
- Generated traffic can be distributed across the gateway instances.

---

### US-023: Reproduce a Control-Plane Failure

**As a developer, I want to simulate control-plane unavailability so that failure-aware local enforcement can be tested.**

#### Acceptance Criteria

- The control plane can be stopped or disconnected during a test.
- Gateway behavior during the outage can be observed.
- Lease expiration behavior can be verified.
- Recovery after the control plane returns can be tested.

---

### US-024: Measure Distributed Enforcement Behavior

**As an engineer, I want to generate controlled traffic against multiple gateways so that I can measure quota distribution, overshoot, latency, and recovery behavior.**

#### Acceptance Criteria

- Traffic can be generated reproducibly.
- Test results can distinguish gateway instances.
- Relevant metrics are collected.
- Results can be compared across coordination strategies or configurations.

---

## 11. Story Prioritization

### Must Have for the Initial Distributed Version

The first implementation should prioritize:

- US-001: Create a Traffic-Control Policy
- US-002: Update a Traffic-Control Policy
- US-004: Enforce a Request Locally
- US-005: Reject Excess Traffic
- US-007: Allocate Local Capacity to a Gateway
- US-008: Request Additional Capacity
- US-011: Register a Gateway
- US-014: Continue Local Enforcement During a Control-Plane Outage
- US-015: Stop Using Expired Authority
- US-016: Recover After Reconnection
- US-017: Propagate a New Policy Version
- US-019: Monitor Traffic Decisions
- US-022: Run Multiple Gateways Locally

### Should Have

The next level of functionality should include:

- US-003: Disable a Policy
- US-006: Apply Hierarchical Limits
- US-009: Renew a Valid Lease
- US-010: Bound Global Capacity Usage
- US-012: Detect an Unavailable Gateway
- US-018: Identify Policy Propagation Lag
- US-020: Investigate a Distributed Coordination Failure
- US-021: View Gateway State
- US-023: Reproduce a Control-Plane Failure

### Later

The following can be expanded after the core distributed coordination model is stable:

- more complex hierarchical policy composition
- adaptive capacity allocation
- advanced fairness algorithms
- multi-region coordination
- richer administrative interfaces
- advanced automated traffic analysis

---

## 12. Summary

The user stories focus on the central engineering challenge of the platform:

> Requests must be enforced locally at gateway nodes while global traffic limits remain coordinated across a distributed system.

The control plane provides authoritative policy and coordination. Gateway nodes perform the latency-sensitive enforcement work using bounded local authority.

The initial implementation should prove this model through a small but complete distributed flow:

1. Create a versioned traffic policy.
2. Register multiple gateway nodes.
3. Allocate bounded capacity to each gateway.
4. Enforce requests locally.
5. Request additional capacity when needed.
6. Continue safely during a temporary control-plane outage.
7. Expire local authority when coordination cannot be renewed.
8. Recover and synchronize when connectivity returns.
