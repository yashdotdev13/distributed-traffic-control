# Distributed Traffic Control — Benchmark & Validation Report

> Status: **Validation in progress**
>
> This document records the implementation status, deployment validation, monitoring verification, benchmark results, and failure-testing plan for the distributed traffic-control system.

---

## 1. Project Overview

The project is a distributed traffic-control system designed around a **Control Plane / Data Plane** model.

At runtime, the request path is conceptually:

```text
HTTP Request
     |
     v
TrafficDecisionEngine
     |
     +--> Policy Resolution
     |
     +--> Local Quota Enforcement
     |
     +--> Global Capacity / Redis Lease
     |
     v
ALLOWED / REJECTED
```

The distributed deployment consists of multiple gateway instances sharing a Redis-backed global capacity pool.

```text
                    +----------------+
                    |     Redis      |
                    | Global Capacity|
                    +-------+--------+
                            |
             +--------------+--------------+
             |              |              |
             v              v              v
       +-----------+  +-----------+  +-----------+
       | Gateway 1 |  | Gateway 2 |  | Gateway 3 |
       |   :8081   |  |   :8082   |  |   :8083   |
       +-----------+  +-----------+  +-----------+
             |
             v
       Traffic Decision
```

---

# 2. Technology Stack

| Component | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1.1 |
| Build | Maven |
| Runtime | Eclipse Temurin JDK/JRE 21 |
| Distributed store | Redis 7 |
| Containerization | Docker / Docker Compose |
| Metrics | Micrometer + Prometheus |
| Visualization | Grafana |
| Load testing | k6 |
| Integration testing | JUnit + Testcontainers |
| Main application | Spring Boot |
| HTTP server | Tomcat 11 |

---

# 3. Implemented Capabilities

## Traffic Policy

Implemented:

- Traffic subjects
- Subject types:
    - USER
    - API_KEY
    - IP_ADDRESS
    - TENANT
- Traffic policies
- Policy types:
    - TOKEN_BUCKET
    - FIXED_WINDOW
    - SLIDING_WINDOW
- Policy status
- Policy registration and management
- Policy resolution
- Default policy
- Policy validation

## Quota

Implemented:

- Quota keys
- Local quota coordination
- Token Bucket
- Fixed Window
- Sliding Window
- Local quota exhaustion handling

The local quota identity is:

```text
policy + subject + resource
```

## Distributed Capacity

Implemented:

- Global capacity registration
- Global capacity identity
- Capacity allocation
- Allocation strategy abstraction
- Fixed allocation strategy
- Bounded lease capacity
- Multi-node allocation
- Concurrent allocation protection

The global capacity identity is:

```text
policy + resource
```

## Lease Lifecycle

Implemented:

- Lease acquisition
- Lease consumption
- Lease renewal
- Lease release
- Lease expiration
- Expired lease reclamation
- Unused capacity recovery

Redis lease acquisition and lifecycle operations use atomic Lua scripts.

---

# 4. Important Distributed-Capacity Design

The system intentionally separates local quota state from distributed global capacity.

```text
LOCAL QUOTA
policy + subject + resource

GLOBAL CAPACITY
policy + resource
```

This allows a gateway to process requests locally while it has usable local quota, and to request distributed capacity through leases when local quota is exhausted.

The current production allocation is bounded using:

```properties
allocation.lease-capacity=10
```

The default global capacity used in the deployed system is:

```text
100 units
```

Therefore the global pool can be distributed in bounded chunks of up to:

```text
10 units per lease
```

This prevents a single gateway from immediately reserving the entire global pool.

---

# 5. Redis Lease Expiration/Reclamation Fix

A correctness issue was identified during distributed validation.

Originally, relying on Redis key expiration alone could delete a lease hash before its remaining capacity was returned to the global pool.

Example:

```text
Global capacity = 100

Gateway A acquires lease = 20

Global available = 80
Lease remaining = 20

Lease expires
```

If the lease hash disappeared without reclaiming the remaining capacity, the system could effectively lose capacity.

The implementation was changed to maintain an expiry registry using a Redis sorted set.

Conceptually:

```text
traffic-control:quota:<policy>:<resource>
        |
        +--> global capacity
        |
        +--> :leases
                |
                +--> lease-key-1 : expiresAt
                +--> lease-key-2 : expiresAt
```

On acquisition, expired leases are reclaimed atomically:

```text
Find expired leases
       |
       v
Read remainingCapacity
       |
       v
Return remaining capacity to global pool
       |
       v
Delete expired lease
       |
       v
Remove registry entry
       |
       v
Allocate requested lease
```

This was validated by the Redis integration suite.

---

# 6. Redis Integration Validation

Focused Redis integration test:

```text
RedisLeaseCoordinatorIntegrationTest
```

Latest result:

```text
Tests run: 8
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

Validated behaviors include:

- Concurrent global-capacity protection
- Capacity removal
- Lease consumption
- Wrong-owner consumption rejection
- Lease renewal
- Wrong-owner renewal rejection
- Lease release and capacity recovery
- Expired lease capacity reclamation

Test infrastructure uses:

```text
Testcontainers
Redis 7 Alpine
```

---

# 7. Containerized Deployment

The application is deployed with Docker Compose.

Current services:

| Service | Container Port | Host Port |
|---|---:|---:|
| gateway-1 | 8081 | 8081 |
| gateway-2 | 8081 | 8082 |
| gateway-3 | 8081 | 8083 |
| redis | 6379 | 6379 |
| prometheus | 9090 | 9090 |
| grafana | 3000 | 3000 |

All containers were successfully started after rebuilding the application image.

Deployment validation:

```text
gateway-1  Up
gateway-2  Up
gateway-3  Up
redis      Up
prometheus Up
grafana    Up
```

---

# 8. Application Runtime Validation

Gateway startup logs confirmed:

```text
Spring Boot 4.1.1
Java 21.0.12
Tomcat 11
Tomcat started on port 8081
Application started successfully
```

A live HTTP request was executed against:

```text
POST /api/v1/traffic/evaluate
```

Response:

```json
{
  "status": "ALLOWED",
  "reason": "Request allowed",
  "remainingCapacity": 99
}
```

Therefore the following path was validated end-to-end:

```text
Docker Container
      |
      v
Spring Boot
      |
      v
HTTP Endpoint
      |
      v
Traffic Decision Engine
      |
      v
ALLOWED
```

---

# 9. Prometheus Validation

Prometheus is configured to scrape the three gateway instances.

Verified targets:

```text
gateway-1:8081  UP
gateway-2:8081  UP
gateway-3:8081  UP
```

All three targets reported:

```text
health = up
lastError = ""
```

Scrape endpoint:

```text
/actuator/prometheus
```

---

# 10. Application Metrics

The application exposes custom traffic-control metrics.

## Decision duration

```text
traffic_control_decision_duration_seconds
```

A summary metric with:

```text
quantile=0.5
quantile=0.95
quantile=0.99
```

is exposed.

## Decision counter

```text
traffic_control_decisions_total
```

Labels:

```text
status="allowed"
status="rejected"
```

## Lease allocation counter

```text
traffic_control_lease_allocations_total
```

Labels:

```text
result="attempt"
result="success"
result="failure"
```

## Local quota exhaustion

```text
traffic_control_quota_exhausted_total
```

The metrics intentionally avoid high-cardinality identifiers such as request ID, subject ID, or resource in the metric labels.

---

# 11. Baseline Load Test

## Test

Single gateway:

```text
k6
  |
  v
gateway-1 :8081
```

Configuration:

```text
VUs: 10
Duration: 30 seconds
```

## Result

```text
Requests:          372,864
Throughput:        12,399 req/s
Average latency:   0.737 ms
Median latency:    0.596 ms
p90 latency:       1.02 ms
p95 latency:       1.41 ms
Maximum latency:   73.85 ms
HTTP failures:     0.00%
Checks passed:     100.00%
```

Thresholds:

```text
p95 < 500 ms       PASS
HTTP failure < 5%  PASS
```

Detailed k6 result:

```text
checks_total:       745,728
checks_succeeded:   745,728
checks_failed:      0

http_reqs:          372,864
http_req_failed:    0.00%

iterations:         372,864
vus:                10
```

### Baseline conclusion

The single gateway handled approximately:

```text
12.4k requests/second
```

with a measured p95 request latency of:

```text
1.41 ms
```

and no HTTP-level failures.

---

# 12. Distributed Load Test

## Test

Traffic was distributed across:

```text
gateway-1 :8081
gateway-2 :8082
gateway-3 :8083
```

Configuration:

```text
VUs: 10
Duration: 30 seconds
```

## Result

```text
Requests:          128,487
Throughput:        4,273 req/s
Average latency:   2.20 ms
Median latency:    1.63 ms
p90 latency:       3.66 ms
p95 latency:       5.10 ms
Maximum latency:   257.87 ms
HTTP failures:     0.00%
Checks passed:     100.00%
```

Thresholds:

```text
p95 < 500 ms       PASS
HTTP failure < 5%  PASS
```

Detailed k6 result:

```text
checks_total:       256,974
checks_succeeded:   256,974
checks_failed:      0

http_reqs:          128,487
http_req_failed:    0.00%

iterations:         128,487
vus:                10
```

### Distributed benchmark conclusion

The distributed deployment remained healthy under load and returned valid traffic decisions for every HTTP request.

The distributed path has higher latency and lower throughput than the single-node baseline because it exercises multiple gateway processes and shared distributed coordination.

The distributed benchmark is therefore a useful measurement of the actual multi-node deployment rather than a simple single-process benchmark.

---

# 13. Distributed Lease Evidence

After the distributed load test, gateway metrics showed that global capacity was actually being coordinated through Redis.

Observed totals:

```text
Total lease allocation attempts:
426,788 + 36,578 + 36,405

Total lease successes:
10

Total lease capacity:
10 leases × 10 units = 100 units
```

Gateway-level observations:

| Gateway | Lease Attempts | Lease Successes |
|---|---:|---:|
| gateway-1 | 426,788 | 10 |
| gateway-2 | 36,578 | 0 |
| gateway-3 | 36,405 | 0 |

This demonstrates that the shared global capacity pool was exhausted through distributed lease acquisition.

The large difference in gateway request counts reflects the routing behavior of the current k6 distributed script. It should not be interpreted as an equal-load distribution benchmark.

---

# 14. Distributed Decision Behavior

The distributed load test generated both allowed and rejected application decisions.

HTTP-level success and application-level allowance are intentionally different concepts.

```text
HTTP 200
   |
   +--> ALLOWED
   |
   +--> REJECTED
```

The k6 `http_req_failed` metric therefore reports whether the HTTP operation failed, not whether traffic admission was allowed.

A correctly functioning traffic-control system may return:

```text
HTTP 200 + REJECTED
```

when capacity has been exhausted.

This is expected and represents successful enforcement rather than an HTTP error.

---

# 15. Monitoring Validation Results

Prometheus successfully reported all three gateway targets as healthy.

The application metrics endpoint was verified directly.

Example current metric state after manual traffic:

```text
traffic_control_decisions_total
allowed   > 0
rejected  >= 0
```

The metrics endpoint also exposes:

```text
traffic_control_decision_duration_seconds
traffic_control_lease_allocations_total
traffic_control_quota_exhausted_total
```

Grafana is deployed and connected to the monitoring stack.

---

# 16. Failure and Recovery Validation — NEXT

The next validation phase is intentional gateway failure.

Goal:

```text
Demonstrate that gateway failure does not stop the remaining gateway nodes.
```

Planned experiment:

```text
1. Start with Redis global capacity = 100.
2. Establish a controlled active lease.
3. Stop gateway-1.
4. Continue traffic through gateway-2 and gateway-3.
5. Observe HTTP decisions and Prometheus metrics.
6. Verify surviving gateways remain available.
7. Restart gateway-1.
8. Verify gateway-1 recovers.
9. Validate lease expiration / capacity reclamation behavior.
```

Important:

A clean experiment must start with known Redis state and must distinguish:

```text
local quota exhaustion
vs
active distributed lease
vs
expired lease
vs
global capacity
```

The failure test should not be confused with a load test that simply exhausts the entire global pool.

---

# 17. Planned Failure-Test Evidence

For the failure experiment, capture:

```text
Redis global capacity before failure
Active lease registry before failure

gateway-1 status before failure
gateway-2 status before failure
gateway-3 status before failure

Traffic decisions while gateway-1 is down

gateway-2 lease allocation metrics
gateway-3 lease allocation metrics

gateway-1 recovery status

Redis global capacity after recovery

Lease registry after expiration/reclamation
```

The final report should include these values rather than relying only on HTTP status.

---

# 18. Benchmark Summary

Current validated performance:

| Scenario | Requests | Throughput | Avg | p90 | p95 | HTTP Failures |
|---|---:|---:|---:|---:|---:|---:|
| Single gateway | 372,864 | 12,399 req/s | 0.737 ms | 1.02 ms | 1.41 ms | 0.00% |
| Distributed 3 gateways | 128,487 | 4,273 req/s | 2.20 ms | 3.66 ms | 5.10 ms | 0.00% |

These results are environment-specific and should be treated as benchmark observations, not universal capacity guarantees.

---

# 19. Validation Checklist

## Core implementation

- [x] Traffic subjects
- [x] Traffic policies
- [x] Policy management
- [x] Policy resolution
- [x] Token Bucket
- [x] Fixed Window
- [x] Sliding Window
- [x] Local quota enforcement
- [x] Traffic decision engine
- [x] Global capacity registration
- [x] Lease acquisition
- [x] Lease consumption
- [x] Lease renewal
- [x] Lease release
- [x] Lease expiration
- [x] Expired capacity reclamation
- [x] Bounded lease allocation
- [x] Multi-node coordination

## Testing

- [x] Redis integration suite
- [x] Concurrent allocation protection
- [x] Lease lifecycle tests
- [x] Expiration reclamation test
- [x] Single-gateway k6 baseline
- [x] Distributed k6 benchmark

## Deployment

- [x] Docker image rebuild
- [x] Docker Compose deployment
- [x] Redis running
- [x] Three gateway instances running
- [x] Prometheus running
- [x] Grafana running
- [x] Live HTTP endpoint validation
- [x] Prometheus target validation

## Remaining

- [ ] Controlled gateway failure experiment
- [ ] Gateway recovery experiment
- [ ] Final lease expiration/reclamation runtime evidence
- [ ] Final benchmark documentation
- [ ] Final README update
- [ ] Final architecture/evidence cleanup
- [ ] Final Git commit and push

---

# 20. Current Project Position

The project has moved beyond the basic implementation stage.

Current state:

```text
                 CORE
                  |
                  v
        Traffic Control Engine
                  |
          +-------+-------+
          |               |
      Local Quota     Distributed
                       Capacity
                           |
                         Redis
                           |
                       Leases
                           |
                  +--------+--------+
                  |        |        |
                  v        v        v
               GW-1     GW-2     GW-3
                  |
                  v
             Observability
                  |
          Prometheus + Grafana
                  |
                  v
          Performance Validation
```

The remaining work is primarily **experimental validation and evidence collection**, not another major core redesign.

---

# 21. Final Validation Philosophy

The project should now be evaluated using observable evidence:

```text
Correctness
    +
Distributed coordination
    +
Failure behavior
    +
Performance
    +
Observability
    +
Deployment
```

The goal of the remaining phase is to demonstrate that the implemented architecture behaves correctly under:

- normal traffic
- capacity exhaustion
- multiple gateway instances
- gateway failure
- gateway recovery
- lease expiration
- capacity reclamation

No additional architectural redesign should be introduced unless a validation experiment exposes a concrete correctness problem.
