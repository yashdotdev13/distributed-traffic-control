# AWS Deployment

This document describes the AWS deployment of the Distributed Traffic Control system, including the compute topology, Redis coordination layer, load balancing, observability, deployment automation, and failure-recovery validation.

---

## 1. Deployment Overview

The application is deployed as a distributed Spring Boot gateway cluster backed by a shared Redis coordination layer.

```text
                         Internet
                            |
                            v
                 +----------------------+
                 |  Application Load    |
                 |      Balancer        |
                 |       HTTP :80       |
                 +----------+-----------+
                            |
              +-------------+-------------+
              |             |             |
              v             v             v
        +-----------+ +-----------+ +-----------+
        | gateway-1 | | gateway-2 | | gateway-3 |
        | EC2       | | EC2       | | EC2       |
        | :8081     | | :8081     | | :8081     |
        +-----+-----+ +-----+-----+ +-----+-----+
              |             |             |
              +-------------+-------------+
                            |
                            v
                 +----------------------+
                 | ElastiCache Redis    |
                 | Shared Coordination  |
                 |      :6379           |
                 +----------------------+
```

The gateways are stateless from the application's perspective. Global quota allocation and lease coordination are stored in Redis, allowing multiple gateway instances to participate in the same distributed capacity pool.

---

## 2. AWS Region

The deployment was created in:

```text
Region: ap-south-1
```

All application infrastructure in this deployment is located in the same AWS region.

---

## 3. Container Image

The application image is published to Amazon Elastic Container Registry (ECR).

Repository:

```text
distributed-traffic-control
```

The deployment uses the `latest` image tag.

The bootstrap process authenticates the EC2 instance with ECR using its attached IAM role, then pulls the application image before starting the gateway container.

---

## 4. IAM

The EC2 instances use an IAM instance profile:

```text
DistributedTrafficControlEC2Profile
```

backed by the role:

```text
DistributedTrafficControlEC2Role
```

The role provides the permissions required for:

- Pulling the application image from Amazon ECR.
- Registering the EC2 instance with AWS Systems Manager (SSM).
- Managing the instance through SSM without SSH.

The deployment intentionally does not require an inbound SSH rule.

---

## 5. VPC and Network Topology

The deployment uses the default VPC in `ap-south-1`.

The gateway nodes are distributed across three Availability Zones:

```text
ap-south-1a
ap-south-1b
ap-south-1c
```

The active gateway instances are:

```text
gateway-1 -> ap-south-1a
gateway-2 -> ap-south-1b
gateway-3 -> ap-south-1c
```

This spreads the gateway layer across multiple Availability Zones.

### Subnets

The deployment uses:

```text
ap-south-1a -> gateway subnet
ap-south-1b -> gateway subnet
ap-south-1c -> existing subnet
```

The subnets have internet routing through the VPC internet gateway so that instances can reach required AWS services when appropriate.

---

## 6. Security Groups

Three primary application security groups are used.

### Application Load Balancer

The ALB security group permits:

```text
TCP 80
Source: 0.0.0.0/0
```

This exposes the public HTTP entry point.

### Gateway

The gateway security group permits:

```text
TCP 8081
Source: ALB security group
```

The gateway application port is therefore reachable from the load balancer rather than being directly exposed to the internet.

### Redis

The Redis security group permits:

```text
TCP 6379
Source: Gateway security group
```

Redis is therefore restricted to the application gateway layer.

---

## 7. AWS Systems Manager

AWS Systems Manager (SSM) is used for administrative access to the EC2 instances.

The deployment configured VPC endpoints for:

```text
com.amazonaws.ap-south-1.ssm
com.amazonaws.ap-south-1.ssmmessages
com.amazonaws.ap-south-1.ec2messages
```

This allows the EC2 instances to communicate with SSM without requiring SSH administration.

SSM was used during deployment for:

- Checking node configuration.
- Starting and inspecting Docker containers.
- Validating Redis connectivity.
- Verifying application health.
- Changing node-specific configuration.
- Performing the gateway failure/recovery experiment.

---

## 8. Redis Coordination Layer

The distributed gateway cluster uses Amazon ElastiCache for Redis.

Replication group:

```text
distributed-traffic-control-redis
```

Configuration:

```text
Redis OSS 7.1
Node type: cache.t4g.micro
Cluster mode: disabled
Nodes: 1
Port: 6379
```

The Redis primary endpoint is injected into the gateway containers through:

```text
REDIS_HOST
REDIS_PORT
```

Redis is used as the shared coordination layer for:

- Global capacity.
- Distributed lease acquisition.
- Lease ownership.
- Lease consumption.
- Lease renewal.
- Lease release.
- Expired lease reclamation.

### Current Redis topology

This deployment intentionally uses a single Redis node as a cost-conscious demonstration environment.

Therefore, this deployment should not be described as Redis high availability.

A production deployment should evaluate:

- Multi-node replication.
- Automatic failover.
- Multi-AZ Redis topology.
- Authentication.
- Encryption in transit.
- Encryption at rest.
- Backup and restore strategy.

---

## 9. EC2 Gateway Cluster

Three EC2 instances run the Spring Boot gateway application.

```text
gateway-1
gateway-2
gateway-3
```

Each instance runs the same container image:

```text
distributed-traffic-control-gateway
```

Application port:

```text
8081
```

Each node receives a unique node identifier:

```text
NODE_ID=gateway-1
NODE_ID=gateway-2
NODE_ID=gateway-3
```

The node identifier is important for distributed lease ownership and allows the system to distinguish leases acquired by different gateway instances.

---

## 10. Container Bootstrap

The reusable bootstrap script is:

```text
scripts/aws/ec2-gateway-user-data.sh
```

The script is intentionally parameterized rather than hardcoding an individual environment.

Required variables:

```text
NODE_ID
AWS_REGION
ECR_REGISTRY
ECR_REPOSITORY
REDIS_HOST
```

Optional variables include:

```text
REDIS_PORT
LEASE_CAPACITY
CONTAINER_NAME
CONTAINER_PORT
```

The bootstrap performs the following sequence:

```text
1. Start SSM Agent
2. Install and start Docker
3. Authenticate against ECR
4. Pull the application image
5. Remove an existing container if necessary
6. Start the gateway container
```

The reusable deployment helper is:

```text
scripts/aws/deploy-gateway.ps1
```

Example:

```powershell
.\scripts\aws\deploy-gateway.ps1 -NodeId gateway-1
```

The helper resolves the current AWS account and Redis endpoint and generates node-specific user-data.

Generated files are ignored through `.gitignore`:

```text
scripts/aws/generated-*-user-data.sh
```

This prevents generated environment-specific configuration from being committed accidentally.

---

## 11. Application Configuration

The gateway container receives the distributed deployment configuration through environment variables.

Example:

```text
REDIS_HOST=<ElastiCache Redis endpoint>
REDIS_PORT=6379
NODE_ID=gateway-1
LEASE_CAPACITY=10
```

The same application image is used on all gateway nodes.

Only node-specific configuration changes between gateway instances.

---

## 12. Application Load Balancer

The public entry point is an internet-facing Application Load Balancer.

Configuration:

```text
Name: distributed-traffic-control-alb
Listener: HTTP :80
```

The listener forwards traffic to:

```text
distributed-traffic-control-tg
```

Target protocol:

```text
HTTP
```

Target port:

```text
8081
```

Health check:

```text
/actuator/health
```

Expected HTTP status:

```text
200
```

The target group contains:

```text
gateway-1
gateway-2
gateway-3
```

At the time of final recovery verification, all three targets were healthy.

---

## 13. Public API Validation

The public endpoint was validated through the Application Load Balancer.

### Health endpoint

```http
GET /actuator/health
```

Expected response:

```json
{
  "status": "UP"
}
```

### Traffic evaluation

The traffic-control API was also tested through the ALB:

```http
POST /api/v1/traffic/evaluate
```

A successful request returned an `ALLOWED` decision with the remaining capacity reported by the distributed quota system.

This confirms the complete request path:

```text
Client
  -> ALB
  -> EC2 Gateway
  -> Redis
  -> Traffic Decision Engine
```

---

## 14. Distributed Load Validation

The three-gateway deployment was exercised using the existing k6 benchmark.

Observed results for the distributed deployment:

```text
Requests:       128,487
Throughput:     4,272.72 req/s
Average:        2.20 ms
p90:            3.66 ms
p95:            5.10 ms
Maximum:        257.87 ms
HTTP failures:  0%
Checks passed:  100%
```

The benchmark also demonstrated distributed lease allocation.

The configured lease capacity was:

```text
10
```

and the successful distributed lease allocations provided:

```text
10 leases × 10 units = 100 units
```

of the globally registered capacity.

Detailed benchmark methodology and results are documented separately in:

```text
docs/benchmark-and-validation.md
```

---

## 15. Observability

The application exposes Spring Boot Actuator and Micrometer metrics.

Important application metrics include:

```text
traffic_control_decision_duration
traffic_control_decisions_total
traffic_control_quota_exhausted_total
traffic_control_lease_allocations_total
```

The deployment also includes:

```text
Prometheus
Grafana
```

Prometheus scrapes the gateway instances, while Grafana provides visualization of application metrics.

The distributed deployment was verified to expose metrics from all gateway nodes.

Metric labels intentionally avoid high-cardinality identifiers such as request IDs.

---

## 16. Failure and Recovery Validation

A gateway failure experiment was performed against the distributed AWS deployment.

### Failure procedure

`gateway-2` was stopped.

The ALB target health transitioned to an unavailable state for that instance while the remaining gateway nodes continued serving traffic.

The two remaining nodes:

```text
gateway-1
gateway-3
```

continued to return successful `ALLOWED` decisions through the public ALB endpoint.

Ten successive public requests were executed successfully during the failure window.

The observed remaining capacities included:

```text
99
98
97
96
95
```

indicating that the distributed application continued processing traffic while one gateway was unavailable.

### Recovery procedure

`gateway-2` was started again.

SSM verification confirmed:

```text
NODE_ID=gateway-2
```

and the Spring Boot health endpoint returned:

```text
UP
```

The ALB target group subsequently reported all three gateway instances as healthy again.

This demonstrates:

```text
Gateway failure
      |
      v
ALB removes failed target
      |
      v
Remaining gateways continue serving traffic
      |
      v
Failed gateway restarts
      |
      v
Health check passes
      |
      v
Gateway returns to the target pool
```

This is the primary AWS failure/recovery validation performed for the deployment.

---

## 17. Deployment Flow

The deployment workflow is:

```text
Source Code
    |
    v
Maven Build
    |
    v
Docker Image
    |
    v
Amazon ECR
    |
    v
EC2 Bootstrap
    |
    +----> Docker pulls image
    |
    +----> Redis configuration injected
    |
    +----> Node ID injected
    |
    v
Spring Boot Gateway
    |
    v
Application Load Balancer
    |
    v
Internet
```

The coordination path is:

```text
Gateway
   |
   v
Redis
   |
   +--> Global quota
   |
   +--> Lease registry
   |
   +--> Lease ownership
   |
   +--> Lease consumption
   |
   +--> Lease reclamation
```

---

## 18. Reproducing the Deployment

The infrastructure used for this deployment was created manually through AWS CLI commands.

A reproducible deployment consists of:

1. Publishing the application image to ECR.
2. Preparing the IAM instance profile.
3. Preparing the VPC networking and security groups.
4. Creating the ElastiCache Redis deployment.
5. Launching EC2 gateway instances.
6. Running the gateway bootstrap.
7. Creating the target group.
8. Registering gateway instances.
9. Creating the ALB and listener.
10. Verifying health and application traffic.
11. Running the distributed validation workload.

The reusable application-level deployment scripts are stored in:

```text
scripts/aws/
```

---

## 19. Current Production Hardening Gaps

This deployment demonstrates the distributed architecture and operational behavior, but it is not intended to represent a fully hardened production environment.

Important production improvements include:

### Transport security

The current ALB listener is HTTP.

A production deployment should use:

```text
HTTPS :443
```

with an appropriate ACM certificate and redirect HTTP to HTTPS.

### Redis resilience

The current Redis deployment is single-node.

A production system should evaluate:

```text
Multi-AZ
Automatic failover
Replication
Backups
Recovery testing
```

### Secret management

Sensitive configuration should be managed through:

```text
AWS Secrets Manager
or
AWS Systems Manager Parameter Store
```

rather than embedding environment-specific values in deployment artifacts.

### Monitoring and alerting

A production environment should add alerts for:

```text
High request latency
High rejection rate
Gateway health failures
Redis failures
Target health degradation
Capacity exhaustion
Container restarts
```

### Infrastructure as Code

The AWS infrastructure can be made fully reproducible through:

```text
Terraform
or
AWS CloudFormation
```

### Autoscaling

The EC2 gateway layer can be extended with:

```text
Auto Scaling Groups
Launch Templates
Target tracking
Load-based scaling
```

---

## 20. Cleanup

The deployment can be removed through the AWS CLI by deleting resources in dependency order.

Before cleanup, identify the active resources:

```powershell
aws ec2 describe-instances `
  --filters "Name=tag:Name,Values=distributed-traffic-control-gateway*" `
  --region ap-south-1
```

Stop or terminate the gateway EC2 instances when they are no longer required.

Delete the load balancer and target group:

```powershell
aws elbv2 delete-load-balancer `
  --load-balancer-arn <ALB_ARN> `
  --region ap-south-1
```

```powershell
aws elbv2 delete-target-group `
  --target-group-arn <TARGET_GROUP_ARN> `
  --region ap-south-1
```

Delete the ElastiCache replication group:

```powershell
aws elasticache delete-replication-group `
  --replication-group-id distributed-traffic-control-redis `
  --region ap-south-1
```

Delete the ECR repository only when the image is no longer required:

```powershell
aws ecr delete-repository `
  --repository-name distributed-traffic-control `
  --force `
  --region ap-south-1
```

Finally, remove infrastructure that was created specifically for this deployment, including:

```text
IAM instance profile
IAM role
VPC endpoints
security groups
subnets
route-table associations
```

Do not delete shared/default VPC infrastructure unless it is known to be unused by other workloads.

---

## 21. Deployment Validation Checklist

The AWS deployment is considered validated for the current scope when all of the following are true:

```text
[✓] ECR image published
[✓] EC2 IAM role configured
[✓] SSM access working
[✓] Three gateway instances running
[✓] Unique gateway node IDs configured
[✓] Redis reachable from gateways
[✓] Redis lease integration validated
[✓] ALB configured
[✓] All gateway targets healthy
[✓] Public health endpoint verified
[✓] Public traffic evaluation verified
[✓] Distributed load test completed
[✓] Prometheus scraping verified
[✓] Grafana available
[✓] Gateway failure tested
[✓] Remaining gateways continued serving traffic
[✓] Failed gateway recovered successfully
[✓] ALB returned recovered gateway to healthy state
```

---

## 22. Summary

The AWS deployment demonstrates a distributed traffic-control gateway architecture with:

```text
3 × EC2 Spring Boot gateways
1 × Application Load Balancer
1 × ElastiCache Redis coordination layer
1 × ECR image repository
Prometheus + Grafana observability
AWS Systems Manager administration
```

The most important distributed property validated in AWS is that gateway instances coordinate through a shared Redis-backed capacity and lease model rather than maintaining isolated global state.

The deployment also demonstrated that a single gateway failure does not prevent the remaining healthy gateways from serving requests through the load balancer, and that the failed gateway can recover and rejoin the target group after restart.

For detailed benchmark methodology, performance measurements, and local validation evidence, see:

```text
docs/benchmark-and-validation.md
```
