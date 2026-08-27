# Problem Statement

## 1. Background

Modern APIs are typically deployed behind multiple gateway or application instances to support high availability and horizontal scalability.

As traffic increases, a single API gateway node is often insufficient to handle all incoming requests. Traffic is therefore distributed across multiple gateway nodes through a load balancer, service mesh, or other traffic-routing mechanism.

This creates a distributed coordination problem when an organization wants to enforce a shared API usage limit.

For example, consider an API consumer with a global quota of:

> 10,000 requests per minute

If requests are distributed across multiple gateway nodes, each node must contribute to enforcing the same shared quota.

The system must ensure that the consumer cannot significantly exceed the intended global limit regardless of which gateway node receives the request.

---

## 2. The Problem

A local rate limiter is straightforward when all requests are processed by a single application instance.

However, in a distributed environment, requests belonging to the same client, API key, tenant, or policy may arrive at different gateway nodes.

Consider three gateway nodes:

```text
                         ┌─────────────┐
                         │   Client    │
                         └──────┬──────┘
                                │
                         Load Balancer
                                │
               ┌────────────────┼────────────────┐
               │                │                │
               ▼                ▼                ▼
         ┌───────────┐    ┌───────────┐    ┌───────────┐
         │ Gateway A │    │ Gateway B │    │ Gateway C │
         └───────────┘    └───────────┘    └───────────┘


```
Suppose the client is allowed:

10,000 requests per minute

If each gateway independently maintains a local limit of 10,000 requests per minute, the client could potentially consume:

Gateway A → 10,000 requests/minute
Gateway B → 10,000 requests/minute
Gateway C → 10,000 requests/minute

This could result in total consumption of up to:

30,000 requests per minute

Although every gateway is enforcing its local configuration correctly, the intended global quota has been violated.

The central problem is therefore:

How can multiple independent gateway nodes enforce a shared global API quota without treating each node as an isolated rate limiter?

3. Why Local Rate Limiting Is Insufficient

A purely local rate limiter maintains traffic-control state independently on each gateway node.

For example:

Gateway A → Local limit: 10,000 requests/minute
Gateway B → Local limit: 10,000 requests/minute
Gateway C → Local limit: 10,000 requests/minute

There is no coordination between the nodes.

As a result:

Each node has an incomplete view of global quota consumption.
The same client can consume capacity through multiple nodes.
Scaling the number of gateway nodes can unintentionally increase the effective quota.
A global limit cannot be reliably enforced using isolated local state alone.
Node failures and restarts can cause locally maintained traffic-control state to become inconsistent.

Therefore, local rate limiting alone is insufficient when a policy must apply across a distributed fleet of gateway nodes.

4. The Naive Centralized Approach

One possible solution is to coordinate every incoming request through a centralized rate-limiting system.

Client Request
      │
      ▼
Gateway Node
      │
      ▼
Central Rate Limit Store
      │
      ▼
Atomic Counter / Rate Limit Operation
      │
      ▼
Allow / Reject

A shared system, such as a centralized data store, could maintain the global rate-limit state and perform an atomic operation for every request.

All gateway nodes would interact with the same shared state.

This provides stronger coordination because each request contributes to a globally shared view of quota consumption.

However, this approach introduces centralized coordination into the critical request path.

Every request may require:

A network call to a shared dependency.
An atomic operation on centralized state.
Coordination between multiple gateway nodes through the same system.
Additional latency before the request can be allowed or rejected.

At high request volumes, the centralized dependency can become a significant scalability and availability concern.

For example, if the system processes:

100,000 requests per second

then the centralized rate-limiting system may also need to handle approximately:

100,000 coordination operations per second

depending on the implementation.

If the centralized dependency becomes slow or unavailable, gateway nodes must also decide how traffic should be handled.

Possible strategies include:

Rejecting requests to preserve strict quota enforcement.
Allowing requests and temporarily relaxing quota enforcement.
Continuing to use previously allocated local capacity.
Applying a degraded or fail-safe traffic-control policy.

Each of these approaches involves trade-offs between consistency, availability, latency, and correctness.

These are distributed-systems problems rather than simple implementation details of a rate-limiting algorithm.

5. Distributed Systems Challenge

The primary challenge of this project is balancing two competing requirements.

5.1 Low-Latency Request Processing

Gateway nodes should make traffic-control decisions with minimal coordination on the critical request path.

Ideally, most requests should be evaluated locally.

Request
   │
   ▼
Gateway
   │
   ▼
Local Traffic-Control Decision
   │
   ├── Allow
   │
   └── Reject

Local evaluation reduces request latency and avoids requiring centralized coordination for every incoming request.

It also reduces the operational dependency of the data plane on a centralized component.

5.2 Shared Global Quota Enforcement

At the same time, all gateway nodes must collectively respect the configured global quota.

Gateway A ──┐
Gateway B ──┤
Gateway C ──┼── Shared Global Quota
Gateway D ──┤
Gateway E ──┘

The difficulty is that local decision-making requires locally available state, while global quota enforcement requires distributed coordination.

The system must therefore distribute sufficient information or capacity to gateway nodes so that they can make local decisions while still maintaining control over total global consumption.

The core engineering challenge can be represented as:

Low-Latency Local Decisions
            +
Distributed Global Coordination
            +
Failure Handling
            +
Scalable Capacity Allocation
            =
Distributed API Traffic Control
6. Proposed Product Direction

api-traffic-control will be designed as a distributed API traffic-control platform based on a control-plane and data-plane model.

The purpose of this separation is to avoid placing all coordination responsibilities directly in the critical request path.

6.1 Control Plane

The control plane is responsible for managing and coordinating distributed traffic-control state.

Its responsibilities may include:

Creating and managing traffic-control policies.
Maintaining policy versions.
Coordinating global quota allocation.
Managing gateway-node membership or registration.
Issuing and tracking quota leases.
Managing lease expiration and renewal.
Propagating policy changes.
Collecting operational and usage information.

The control plane is primarily responsible for coordination and management.

It is not intended to process every incoming API request.

6.2 Data Plane

The data plane is responsible for evaluating incoming API traffic.

Gateway nodes in the data plane should be able to:

Identify the applicable traffic-control policy.
Determine the identity associated with a request.
Evaluate locally available quota or capacity.
Make low-latency allow or reject decisions.
Request additional capacity when required.
Operate using locally available state when possible.
Report relevant usage and operational information.

The data plane should avoid centralized coordination for every request whenever possible.

7. Quota Leasing Direction

The initial design direction for this project is to explore bounded quota leasing.

Instead of requiring every request to obtain permission from a central coordinator, the control plane allocates a limited amount of quota capacity to individual gateway nodes.

For example, consider a global quota of:

10,000 requests per minute

The available capacity could be distributed between three gateway nodes:

Global quota: 10,000 requests/minute

                ┌────────────────────────┐
                │    Quota Coordinator   │
                │                        │
                │ Available Capacity     │
                │       10,000           │
                └────────────┬───────────┘
                             │
                  Allocates bounded capacity
                             │
          ┌──────────────────┼──────────────────┐
          │                  │                  │
          ▼                  ▼                  ▼
     Gateway A           Gateway B           Gateway C
     Lease: 3,000        Lease: 4,000        Lease: 3,000

Each gateway can then process requests using its locally allocated capacity.

For example:

Request
   │
   ▼
Gateway A
   │
   ▼
Does local lease have capacity?
   │
   ├── Yes ──► Allow request
   │
   └── No ───► Request additional capacity

This means that most requests do not require direct coordination with the central control plane.

The gateway consumes locally allocated capacity until:

The lease capacity is exhausted.
The lease is close to expiration.
The policy changes.
The gateway requires additional quota.

The gateway can then coordinate with the control plane when necessary.

The exact implementation of quota leasing, including lease duration, allocation size, renewal strategy, and recovery behavior, will be defined during later phases.

8. Core Design Question

The central design question for api-traffic-control is:

How can a distributed system enforce a shared global API quota across multiple gateway nodes while allowing most traffic-control decisions to be made locally?

The project will investigate the trade-offs between:

Global consistency and request latency.
Centralized coordination and horizontal scalability.
Strict quota enforcement and system availability.
Local autonomy and distributed coordination.
Fast policy propagation and configuration consistency.
Efficient quota utilization and bounded over-allocation.
Failure recovery and operational complexity.

The project will intentionally treat these trade-offs as first-class engineering decisions.

9. Intended System Guarantee

The initial version of the system will not attempt to maintain perfectly synchronized global state for every individual request.

Such an approach would require stronger coordination in the request path and would reduce the benefits of local decision-making.

Instead, the intended design direction is bounded distributed quota enforcement.

Gateway nodes receive a limited amount of centrally coordinated capacity and consume that capacity locally.

The intended system property is:

Global quota consumption is controlled through bounded quota allocation, allowing gateway nodes to make low-latency local decisions while limiting the amount of independently consumable capacity distributed across the gateway fleet.

This means the system accepts that perfect per-request global synchronization is not the primary goal.

Instead, the system focuses on controlling and bounding distributed capacity while reducing centralized coordination.

The exact consistency guarantees, overshoot limits, and failure behavior will be defined in the non-functional requirements and subsequent architecture phases.

10. Success Criteria

The project will be considered successful if it demonstrates the following capabilities.

10.1 Shared Policy Enforcement

Multiple gateway nodes must be able to enforce the same traffic-control policy.

A policy must not become independent simply because additional gateway nodes are added.

10.2 Local Request Decisions

Most incoming requests should be evaluated locally using gateway-resident state.

The system should avoid requiring centralized coordination for every request.

10.3 Bounded Quota Allocation

A gateway node must not be able to consume unlimited global capacity independently.

The amount of capacity available to each gateway should be bounded by the distributed allocation mechanism.

10.4 Distributed Coordination

Gateway nodes must be able to coordinate with the control plane to acquire, renew, or update locally available quota.

10.5 Failure Handling

The system must define how allocated capacity is handled when:

A gateway node crashes.
A gateway becomes unreachable.
A control-plane component becomes unavailable.
Network communication fails.
A gateway restarts.
A lease expires.
10.6 Policy Propagation

Traffic-control policies must be capable of being updated and propagated across distributed gateway nodes.

The system must define how policy versions and stale configurations are handled.

10.7 Observability

The system should provide sufficient operational visibility into:

Traffic-control decisions.
Quota consumption.
Lease allocation.
Lease expiration.
Gateway-node health.
Policy versions.
Rejected requests.
Control-plane communication.
10.8 Reproducible Distributed Experiments

The project should demonstrate realistic distributed-system scenarios, including:

Multiple gateway nodes.
Uneven traffic distribution.
Quota exhaustion.
Gateway failure.
Control-plane unavailability.
Policy updates.
Lease expiration and recovery.

The architecture and its trade-offs should be measurable and reproducible.

11. Product Boundary

api-traffic-control is a distributed traffic-control platform.

It is not intended to become a complete API gateway or full API management product.

The core responsibility of the system is to determine whether traffic should be allowed or rejected according to configured policies and distributed quota state.

The following concerns are outside the primary responsibility of the project:

API business logic.
Request routing.
Load balancing.
Authentication implementation.
Application-level authorization.
Request transformation.
Response transformation.
API documentation hosting.
API monetization.
Business-service discovery.

These capabilities may exist in an environment where the platform is deployed, but they are not the primary distributed-systems problem being addressed.

The platform should remain focused on distributed traffic control and quota coordination.

12. Project Focus

The primary engineering focus of api-traffic-control is not simply implementing a rate-limiting algorithm.

The project focuses on the distributed problems that emerge when a shared API quota must be enforced across multiple independently operating nodes.

These problems include:

Distributed state management.
Capacity allocation.
Coordination between nodes.
Local versus centralized decision-making.
Consistency trade-offs.
Failure detection and recovery.
Lease expiration.
Policy versioning.
Configuration propagation.
Scalability.
Availability.
Observability.

The selected rate-limiting algorithms and data structures are implementation mechanisms.

The primary value of the project comes from the distributed architecture and the engineering trade-offs involved in enforcing shared traffic-control policies at scale.

Summary

api-traffic-control addresses the distributed coordination problem of enforcing shared API quotas across multiple independently operating gateway nodes.

A purely local rate limiter cannot enforce a global quota because each node maintains only an isolated view of traffic consumption.

A centralized per-request approach provides stronger coordination but introduces a shared dependency into the critical request path and can create scalability, latency, and availability concerns.

The proposed product direction explores a control-plane and data-plane architecture in which the control plane coordinates policies and bounded quota allocation while gateway nodes in the data plane make low-latency local allow or reject decisions.

The central engineering challenge of the project is to balance local autonomy with global coordination while maintaining bounded quota enforcement, scalable request processing, and well-defined behavior during distributed failures.