# Security Policy

## Supported versions

Security fixes are applied to the actively maintained default branch.

| Version | Supported |
|---|---|
| `main` | ✅ |
| Older commits/releases | ❌ |

This policy may change as formal releases are introduced.

## Reporting a vulnerability

Please do **not** report security vulnerabilities through public GitHub issues.

A responsible disclosure should be made privately to the project maintainer through the contact method associated with the GitHub profile:

https://github.com/yashdotdev13

When reporting a vulnerability, include:

- A clear description of the issue
- The affected component or file
- Steps to reproduce
- Potential impact
- A minimal proof of concept when safe to provide
- Any suggested mitigation

Please avoid including credentials, production secrets, personal data, or other sensitive information in the report.

## What happens after a report

The maintainer will:

1. Acknowledge the report when reasonably possible.
2. Validate and assess the reported issue.
3. Determine severity and affected versions.
4. Prepare a fix or mitigation.
5. Coordinate disclosure timing when appropriate.

Please allow reasonable time for investigation before making vulnerability details public.

## Security-sensitive areas

Extra care is expected around:

- Redis authentication and TLS
- Lease ownership and authorization
- Global capacity accounting
- Authentication and management endpoints
- Secrets and deployment configuration
- Infrastructure and cloud IAM permissions
- Observability data that could contain sensitive identifiers
