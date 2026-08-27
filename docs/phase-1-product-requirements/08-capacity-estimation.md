# Capacity Estimation

## 1. Purpose

This document defines the initial capacity assumptions and sizing model for the API Traffic Control Platform.

The purpose of this estimation is not to predict exact production traffic. The project is being built as a production-oriented distributed systems platform, so the estimates provide a concrete workload model for making architectural decisions.

The capacity model will be used to evaluate:

- gateway throughput
- control-plane load
- distributed quota allocation frequency
- network coordination overhead
- storage requirements
- local memory requirements
- observability volume
- failure and scaling scenarios

All numbers in this document are initial engineering assumptions and can be revised after benchmark experiments.

---

## 2. Initial Workload Assumptions

For the first production-oriented version, assume the platform protects APIs with the following traffic profile.

| Metric | Initial Assumption |
|---|---:|
| Average requests per second | 10,000 RPS |
| Peak requests per second | 50,000 RPS |
| Target peak headroom | 2x |
| Design peak capacity | 100,000 RPS |
| Initial gateway nodes | 5 |
| Expected gateway scale | 5 to 50 nodes |
| Initial active policies | 10,000 |
| Initial protected clients or tenants | 100,000 |
| Policy updates | Low frequency |
| Gateway registrations | Low frequency |
| Capacity allocation operations | Much lower than request traffic |

The most important distinction is between **data-plane traffic** and **control-plane traffic**.

The system must be designed for potentially tens of thousands of API requests per second, but the control plane should not receive tens of thousands of coordination requests per second simply because API traffic increases.

---

## 3. Data-Plane Throughput

### 3.1 Average Traffic

Assuming an average of:

> 10,000 requests per second

The approximate request volume is:

- 600,000 requests per minute
- 36 million requests per hour
- 864 million requests per day

The platform does not need to persist a database record for every traffic-control decision.

The normal decision path should primarily perform:

1. request identity extraction
2. policy lookup
3. local quota or token evaluation
4. allow or reject decision
5. metric updates

The decision should normally complete using local memory.

---

### 3.2 Peak Traffic

Assuming a peak workload of:

> 50,000 requests per second

and an initial deployment of:

> 5 gateway nodes

the average peak load per gateway is:

> 10,000 RPS per gateway

With uneven load distribution and operational headroom, the system should be designed so that a gateway is not considered fully utilized at exactly its average assigned load.

The platform should therefore support adding gateway instances horizontally.

---

### 3.3 Design Capacity

For architecture decisions, use:

> 100,000 RPS design capacity

This provides approximately 2x headroom over the initial peak assumption.

At this level, a design requiring a remote control-plane request for every API request would create:

> 100,000 coordination requests per second

This is intentionally unacceptable for the project architecture.

The primary reason for local enforcement and quota leasing is to reduce this coordination frequency.

---

## 4. Gateway Capacity Model

Assume the initial system has:

> 5 gateway nodes

At a design capacity of 100,000 RPS:

100,000 / 5 = 20,000 RPS per gateway

Therefore, the initial engineering target is approximately:

> 20,000 RPS per gateway at design capacity

This is a target for the overall deployment model, not a guarantee that every hardware configuration will achieve the same number.

Actual throughput will depend on:

- request processing
- network stack
- policy lookup implementation
- local synchronization strategy
- JVM configuration
- CPU allocation
- memory allocation
- downstream proxying behavior

Benchmarking will later validate the actual implementation.

---

## 5. Why Per-Request Coordination Does Not Scale

Consider a centralized rate-limiting design where every request performs a remote operation.

At 100,000 RPS:

- 100,000 remote coordination operations per second
- 6 million operations per minute
- 360 million operations per hour

This creates several problems:

1. The coordination service becomes part of every request's critical path.
2. Network latency is added to every traffic decision.
3. Coordination service outages directly affect request processing.
4. Scaling API traffic requires proportional scaling of centralized coordination.
5. Cross-gateway global limits become dependent on continuous connectivity.

The project intentionally avoids this model.

Instead, gateways receive bounded local authority and consume it locally.

---

## 6. Quota Lease Estimation

The exact lease size will depend on policy configuration and traffic patterns.

For an example global policy of:

> 1,000,000 allowed requests per minute

Assume the control plane allocates capacity in leases of:

> 10,000 requests

Then the theoretical maximum number of lease allocations required to distribute one full minute of capacity is:

1,000,000 / 10,000 = 100 lease allocations

This is dramatically smaller than coordinating all:

> 1,000,000 individual requests

The lease size creates an important trade-off.

### Larger Leases

Advantages:

- fewer control-plane requests
- lower coordination overhead
- less network traffic

Disadvantages:

- more unused capacity may remain stranded on a gateway
- larger potential overshoot or allocation imbalance
- slower redistribution of capacity

### Smaller Leases

Advantages:

- tighter global coordination
- better redistribution
- lower potential unused local authority

Disadvantages:

- more control-plane operations
- increased coordination overhead
- greater dependency on control-plane availability

The implementation should make lease size configurable so these trade-offs can be benchmarked.

---

## 7. Gateway Lease Renewal Traffic

Assume:

- 50 gateway nodes
- 10 active policies per gateway
- one lease renewal or allocation interaction every 30 seconds for active policies

The approximate coordination rate is:

50 x 10 / 30 = 16.67 operations per second

Therefore, the control plane may process approximately:

> 17 lease coordination operations per second

for this simplified workload.

Even after accounting for bursts, retries, and multiple policy dimensions, this remains far below the API request rate.

This demonstrates the intended architectural separation:

> Data-plane request volume may be tens of thousands of requests per second while control-plane coordination remains orders of magnitude lower.

---

## 8. Policy Storage Estimation

Assume:

> 10,000 active policies

If an average serialized policy and associated metadata require approximately:

> 2 KB

then the raw storage requirement is approximately:

10,000 x 2 KB = 20 MB

This is small for persistent storage.

Policy storage volume is therefore not expected to be the primary scaling challenge.

The more important requirements are:

- correct versioning
- reliable updates
- efficient distribution
- consistency of authoritative state

---

## 9. Gateway State Estimation

A gateway may maintain local state for:

- policies
- policy versions
- quota leases
- token counters
- active client keys
- temporary coordination metadata

Assume a gateway has:

> 10,000 active local traffic keys

If each key requires approximately:

> 1 KB of effective in-memory state

then the estimated raw state is:

> 10 MB

Actual JVM memory usage will be higher because of object overhead and runtime structures.

For this reason, the implementation should avoid unnecessary per-request object allocation and unbounded in-memory maps.

Local state must have explicit lifecycle management.

---

## 10. Gateway Registration Scale

The initial system targets:

> 5 to 50 gateway nodes

This is intentionally modest because the primary goal is to validate the distributed coordination model.

The architecture should not assume a fixed number of gateways.

A gateway lifecycle model should support:

- startup registration
- heartbeat or activity reporting
- temporary disconnection
- lease expiry
- restart
- recovery
- addition of new nodes

The control plane should treat gateway membership as dynamic.

---

## 11. Network Coordination Estimate

The system should avoid coordination traffic proportional to API request traffic.

The major distributed messages are expected to include:

- gateway registration
- policy propagation
- policy acknowledgements
- capacity allocation requests
- lease issuance
- lease renewal
- usage reports
- gateway heartbeats

Assume a typical coordination message is approximately:

> 1 KB

At 100 coordination operations per second, the raw payload volume is approximately:

> 100 KB per second

or approximately:

> 8.64 GB per day

This is only a rough upper-level estimate and excludes protocol overhead.

The key design objective is not minimizing coordination traffic to zero. It is ensuring that coordination traffic grows primarily with:

- number of gateways
- number of active policies
- lease frequency

rather than directly with every incoming API request.

---

## 12. Usage Reporting Estimate

Usage reporting does not necessarily need to occur for every request.

A gateway may aggregate usage and report periodically.

For example, assume:

- 50 gateway nodes
- 100 active policy counters per gateway
- reporting every 10 seconds

The system produces approximately:

50 x 100 / 10 = 500 counter updates per second

The exact design may batch multiple counters into one message.

Therefore, the preferred model is:

> aggregate locally, report periodically, and reconcile using bounded coordination

rather than:

> synchronously report every request

---

## 13. Observability Volume

Metrics should be preferred over high-cardinality per-request logs for normal traffic decisions.

Logging every allowed request at 100,000 RPS would create:

- 100,000 log events per second
- 6 million log events per minute

This is unnecessary and operationally expensive.

Instead:

### Metrics should record

- allowed request count
- rejected request count
- decision latency
- lease allocation count
- lease renewal failures
- policy propagation lag

### Logs should focus on

- failures
- policy changes
- gateway lifecycle events
- lease state changes
- unusual coordination behavior

### Traces should focus on

- control-plane workflows
- sampled request flows where useful
- distributed failure investigation

---

## 14. Storage Growth Considerations

The authoritative persistent database is expected to store relatively low-volume control-plane data such as:

- policies
- policy versions
- gateway metadata
- lease metadata where persistence is required
- administrative audit records

High-frequency request counters should not automatically be persisted as individual database rows.

If historical analytics are added later, aggregated events or time-series storage should be considered separately from authoritative control-plane storage.

---

## 15. Failure Capacity Considerations

Suppose the system runs:

> 10 gateway nodes

and one gateway fails.

The remaining nodes should be capable of handling redistributed traffic.

This means production deployment should not target 100% utilization under normal operation.

A practical design principle is:

> Keep sufficient headroom so that the failure of one or more nodes does not immediately overload all remaining nodes.

The exact redundancy factor will depend on deployment cost and workload characteristics.

For the project, failure experiments should demonstrate:

- gateway failure
- control-plane failure
- network disconnection
- gateway restart
- stale lease expiry

---

## 16. Latency Budget

The traffic-control component should contribute only a small portion of total API latency.

An example internal budget for the local decision path is:

| Operation | Target |
|---|---:|
| Request identity extraction | < 0.5 ms |
| Local policy lookup | < 0.5 ms |
| Local quota decision | < 1 ms |
| Metric update | asynchronous or low overhead |
| Total added p95 overhead | < 5 ms |

These are engineering targets.

Actual measurements will be established during the benchmarking phase.

The design should avoid introducing:

- synchronous database calls
- synchronous remote cache calls
- synchronous control-plane calls

into the normal request enforcement path.

---

## 17. Initial Sizing Summary

| Component | Initial Target |
|---|---|
| Average traffic | 10,000 RPS |
| Peak traffic | 50,000 RPS |
| Design traffic | 100,000 RPS |
| Initial gateways | 5 |
| Gateway scale target | 5 to 50 |
| Approximate design load per initial gateway | 20,000 RPS |
| Active policies | 10,000 |
| Protected clients or tenants | 100,000 |
| Normal decision path | Local |
| Per-request control-plane coordination | No |
| Target p95 added enforcement latency | < 5 ms |
| Target p99 added enforcement latency | < 10 ms |

---

## 18. Architectural Implications

These capacity estimates lead directly to several architectural decisions.

### Decision 1: Separate Control Plane and Data Plane

The request path must remain independent from most control-plane operations.

### Decision 2: Use Local Enforcement

Gateway nodes must be capable of making normal traffic decisions using locally available state.

### Decision 3: Use Bounded Local Authority

Global quota capacity should be distributed in bounded allocations rather than requiring centralized coordination for every request.

### Decision 4: Keep Coordination Frequency Low

Lease allocation, renewal, reporting, and policy propagation should occur at a frequency much lower than API request processing.

### Decision 5: Design for Horizontal Gateway Scaling

Gateway nodes should be independently scalable.

### Decision 6: Aggregate High-Frequency Information

Usage and observability information should be aggregated where appropriate instead of creating persistent or network activity for every request.

---

## 19. Assumptions to Validate Later

The following assumptions must be validated through experiments:

- achievable RPS per gateway
- actual local decision latency
- memory usage per active traffic key
- optimal lease size
- lease renewal frequency
- bounded quota overshoot
- policy propagation latency
- control-plane throughput under gateway churn
- recovery time after control-plane failure

The benchmark and experiment phases of the project will replace these assumptions with measured results.

---

## 20. Summary

The initial capacity model assumes a system capable of supporting a design workload of approximately 100,000 API requests per second while keeping most traffic-control decisions local to gateway nodes.

The central architectural principle is:

> Request traffic and coordination traffic must scale differently.

API request volume may grow to tens of thousands of requests per second, while the control plane should primarily coordinate policies, bounded capacity allocations, and aggregated state.

This capacity model therefore supports the project's main distributed systems objective: scalable local enforcement with coordinated global traffic control.
