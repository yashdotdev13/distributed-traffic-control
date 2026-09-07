# Contributing to Distributed Traffic Control

Thank you for considering a contribution to Distributed Traffic Control.

This project focuses on distributed traffic control, Redis-backed coordination, capacity leasing, observability, and production-oriented distributed-systems engineering.

## Before you start

Please read:

- [README](README.md)
- [Benchmark & Validation](docs/benchmark-and-validation.md)
- [AWS Deployment](docs/aws-deployment.md)
- the relevant design documentation under `docs/`

For feature work, check the existing open issues before creating a new one. Prefer contributing to an existing issue so that the scope and acceptance criteria are clear.

## Development setup

### Requirements

- Java 21
- Docker Desktop
- Git

### Run the test suite

Linux/macOS:

```bash
./mvnw -B verify
```

Windows PowerShell:

```powershell
.\mvnw.cmd -B verify
```

The integration suite uses Testcontainers for Redis-backed tests, so Docker must be available.

### Run the distributed local environment

```bash
docker compose build
docker compose up -d
```

Check the services:

```bash
docker compose ps
```

The gateway instances are exposed locally on ports `8081`, `8082`, and `8083`.

## Picking an issue

Look for issues labeled:

- `good first issue`
- `help wanted`
- `enhancement`
- `documentation`

Before starting significant work, leave a comment on the issue describing what you intend to change. This helps avoid duplicated effort.

## Branching

Create a focused branch from `main`:

```bash
git checkout main
git pull origin main
git checkout -b feature/<short-description>
```

Examples:

```text
feature/redis-readiness
feature/otel-tracing
feature/lease-strategy
```

Do not work directly on `main`.

## Implementation guidelines

Keep changes focused and avoid unrelated refactoring.

For distributed coordination changes:

- Preserve atomicity of multi-step Redis state transitions.
- Prefer Redis Lua scripts where a distributed mutation must be atomic.
- Do not weaken lease ownership checks.
- Preserve bounded lease allocation.
- Preserve explicit expiration and capacity reclamation semantics.
- Avoid request-specific identifiers as metric labels.

For configuration changes:

- Prefer environment/configuration-driven behavior.
- Do not commit credentials, tokens, endpoints containing secrets, or local machine-specific configuration.

For tests:

- Add or update tests for behavior that changes.
- Use Testcontainers for Redis behavior that depends on real Redis semantics.
- Keep existing tests green.

## Commit messages

Use concise, imperative commit messages.

Examples:

```text
feat: add Redis readiness healthcheck
fix: prevent lease renewal after expiration
test: cover concurrent lease acquisition
docs: document local startup requirements
```

## Pull requests

Before opening a PR:

```bash
git status
.\mvnw.cmd -B verify
```

For Windows PowerShell, also make sure the working tree contains only the intended changes.

A good PR should explain:

1. What changed
2. Why it changed
3. How it was tested
4. Any design or compatibility considerations

Keep one logical change per PR where practical.

## Pull request expectations

PRs should:

- reference the related issue, for example `Closes #12`
- include tests for behavior changes
- preserve backward compatibility unless the issue explicitly requires a breaking change
- avoid unrelated formatting or refactoring
- keep CI green

## Questions and discussions

For design changes with significant distributed-systems impact, open an issue before implementing a large change. Explain the trade-offs and proposed approach so the design can be discussed before code is written.

Thank you for helping improve the project.
