# OpenTelemetry Tracing

This document describes the OpenTelemetry tracing integration in **Distributed Traffic Control**, including the instrumentation, configuration, local Jaeger setup, and verification steps.

## Overview

The application uses Spring Boot's Micrometer Observation/OpenTelemetry integration to export distributed traces using OTLP (OpenTelemetry Protocol). Jaeger is used as the local trace backend and UI.

The intent is to make the traffic-decision path observable and to distinguish local quota evaluation from distributed lease operations coordinated through Redis.

## What is instrumented

The application adds observations around the traffic-decision workflow and Redis lease operations.

### Traffic-decision workflow

| Observation name | Purpose |
| --- | --- |
| `traffic.decision` | Overall traffic-decision operation |
| `traffic.policy.lookup` | Policy lookup |
| `traffic.quota.evaluate` | Quota-consumption evaluation |
| `traffic.lease.allocate` | Lease allocation request |
| `traffic.lease.consume` | Consumption against an allocated lease |

### Redis lease operations

| Observation name | Purpose |
| --- | --- |
| `redis.lease.acquire` | Redis-backed lease acquisition |
| `redis.lease.consume` | Redis-backed lease consumption |
| `redis.lease.renew` | Redis-backed lease renewal |
| `redis.lease.release` | Redis-backed lease release |

The lease spans help separate time spent in Redis/distributed coordination from time spent evaluating the local quota decision.

> A span is emitted only when the corresponding code path executes. For example, lease-acquisition spans require a request that reaches the lease-allocation path.

## Implementation locations

The main implementation points are:

- `src/main/java/com/yashdotdev/distributed_traffic_control/traffic/TrafficDecisionEngine.java` — wraps the decision flow and its key operations in observations.
- `src/main/java/com/yashdotdev/distributed_traffic_control/lease/RedisLeaseCoordinator.java` — wraps Redis lease operations in observations.
- `src/main/java/com/yashdotdev/distributed_traffic_control/config/TrafficControlConfiguration.java` — supplies the shared `ObservationRegistry` to the components.
- `src/main/resources/application.properties` — configures the OTLP trace endpoint and sampling probability.
- `docker-compose.yml` — configures Jaeger and the OTLP endpoint for the gateway containers.

## Configuration

The application configuration uses environment-variable overrides so the same application can run locally or in Docker.

Relevant settings in `src/main/resources/application.properties`:

```properties
# OpenTelemetry tracing
management.opentelemetry.tracing.export.otlp.endpoint=${OTEL_EXPORTER_OTLP_TRACES_ENDPOINT:http://localhost:4318/v1/traces}
management.tracing.sampling.probability=${TRACING_SAMPLING_PROBABILITY:1.0}

# Do not export application metrics to Jaeger
management.otlp.metrics.export.enabled=false
```

For Docker Compose, each gateway is configured with:

```yaml
OTEL_EXPORTER_OTLP_TRACES_ENDPOINT: http://jaeger:4318/v1/traces
TRACING_SAMPLING_PROBABILITY: "1.0"
```

The hostname `jaeger` is the Compose service name and is reachable by the gateways on the Compose network. The trace endpoint uses OTLP over HTTP on port `4318`.

A sampling probability of `1.0` samples every trace. This is useful for local development and debugging; production deployments should choose a sampling strategy appropriate for their traffic volume and observability requirements.

## Local Jaeger setup

The Compose configuration includes a Jaeger service exposing:

- `16686` — Jaeger web UI
- `4317` — OTLP over gRPC
- `4318` — OTLP over HTTP

From the project root, build the application image and start the services:

```powershell
docker build -t distributed-traffic-control:latest .
docker compose up -d
docker compose ps
```

Wait until the three gateway containers and Redis are healthy. Open the Jaeger UI at:

<http://localhost:16686>

In Jaeger, select the `distributed-traffic-control` service and click **Find Traces**.

## Generate a test trace

The traffic evaluation endpoint is:

`POST http://localhost:8081/api/v1/traffic/evaluate`

Example PowerShell request:

```powershell
$body = @{
    requestId = "trace-test-001"
    subject = @{
        type = "USER"
        subjectId = "demo-user"
    }
    resource = "demo-resource"
    requestedAt = (Get-Date).ToUniversalTime().ToString("o")
} | ConvertTo-Json -Depth 5

Invoke-RestMethod `
    -Uri "http://localhost:8081/api/v1/traffic/evaluate" `
    -Method Post `
    -ContentType "application/json" `
    -Body $body
```

This example uses a demo identifier only to exercise the endpoint. Do not attach request IDs, subject IDs, API keys, or other sensitive/high-cardinality identifiers as span attributes.

To inspect logs:

```powershell
docker compose logs --since=5m gateway-1
docker compose logs --since=5m jaeger
```

## Troubleshooting

### Jaeger shows no services or traces

1. Check that Jaeger and all gateways are running:

   ```powershell
   docker compose ps
   ```

2. Confirm the gateway environment points to `http://jaeger:4318/v1/traces`, not `localhost` from inside a container.
3. Check gateway logs for `Failed to export spans` and HTTP status errors.
4. Send a request to the traffic evaluation endpoint and refresh Jaeger.

### OTLP exporter returns HTTP 404

Check that the traces endpoint is configured specifically as:

`http://jaeger:4318/v1/traces`

Use `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` for the trace-specific endpoint. Do not use a traces URL as a shared metrics endpoint. The application metrics are intended to remain available through the existing Prometheus/Actuator setup.

### Lease spans do not appear

Lease spans are conditional on the request reaching lease acquisition, consumption, renewal, or release. A request that is allowed immediately by the quota coordinator may not acquire a lease. Use a test scenario that reaches the lease path to verify those spans.

## Privacy and cardinality

Do not add request IDs, user IDs, API keys, raw subject IDs, or similarly unique/sensitive values as span attributes. Prefer bounded, low-cardinality attributes such as operation names and outcome categories when additional attributes are needed.

## Validation status

During local verification, the gateway containers and Redis reached healthy status, a request to `/api/v1/traffic/evaluate` returned `ALLOWED`, and Jaeger displayed the `distributed-traffic-control` service and traces.

That confirms the local trace-export path is working. To fully validate the instrumentation, inspect individual trace details and exercise both the ordinary decision path and a request that reaches Redis lease operations.
