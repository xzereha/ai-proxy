# Security Analysis Report -- OWASP Top 10 (2021)

**Project:** AI Proxy (OpenAI reverse proxy)
**Technology:** Spring Boot 4.0.6 / WebFlux / Java 26 / Gradle

---

## Security Boundary Context

This application is a **stateless OpenAI proxy** that acts as a middle layer between clients and the OpenAI API. It holds **zero user data** -- no accounts, no PII, no sessions, no database. The sole asset at risk is the **OpenAI API key** and the **financial budget** tied to it (billed per-token usage to GPT-4o). This shapes the threat model: we are protecting a resource (OpenAI API credits), not user data. The three vulnerabilities below are selected and prioritized with this boundary in mind.

---

## Vulnerability 1 -- A01: Broken Access Control

### OWASP Category

**A01:2021 - Broken Access Control**

### Identification

**Location:** `src/main/java/com/example/ai/config/SecurityConfig.java:15-31` (original), `src/main/java/com/example/ai/controller/AiController.java`

The original `SecurityConfig` configured every meaningful API endpoint -- including `POST /api/v1/chat` and `GET /api/v1/models` -- with `.permitAll()`:

```java
.pathMatchers(
    "/api/v1/models",
    "api/v1/chat",
    "/swagger-ui",
    "/swagger-ui/**",
    "/api-docs/**",
    "/actuator/health")
.permitAll()
```

The catch-all `.anyExchange().authenticated()` was **dead code**: all application routes were explicitly opened. This meant:

- Anyone who discovered the service URL could send arbitrary prompts to GPT-4o via `POST /api/v1/chat`, consuming the OpenAI budget.
- Anyone could enumerate all available OpenAI models via `GET /api/v1/models`.
- No authentication of any kind was required -- the proxy was fully public.

### Mitigation (Not Implemented)

This is an internal development tool, not a public-facing service. The `SecurityConfig` (`src/main/java/com/example/ai/config/SecurityConfig.java`) permits all requests without authentication so the API remains accessible via browser for local development.

For a public-facing deployment, access control should be added (e.g., API key validation via a `WebFilter` that reads from an environment variable or Vault).

### Analysis & Prioritization

**Severity: Critical.**
**Priority: 1 (deferred for internal use).**

An open AI proxy is the single most dangerous configuration possible in this codebase if deployed publicly. Without access control, anyone who knows the service URL can:

- Send unlimited prompts to GPT-4o, incurring arbitrary costs.
- Exhaust the monthly OpenAI budget in minutes with a simple script.
- Cause the API key to be rate-limited or revoked by OpenAI.

Since this application is an internal development tool accessible only to a small, known team, the risk is accepted. Before any public or production deployment, access control must be implemented.

---

## Vulnerability 2 -- A06: Vulnerable and Outdated Components

### OWASP Category

**A06:2021 - Vulnerable and Outdated Components**

### Identification

**Location:** `build.gradle.kts` (entire dependency tree)

The project had **no software composition analysis** (SCA) integrated. The `build.gradle.kts` declared 15+ production dependencies but:

- No OWASP Dependency-Check or similar SCA plugin was configured.
- The CI pipeline ran `./gradlew build` and `./gradlew test` but no vulnerability scan.
- Several dependencies were **imported but never used** (`bucket4j-core`, `resilience4j-circuitbreaker`, `resilience4j-retry`, `resilience4j-reactor`, `spring-retry`, `spring-aspects`), adding unnecessary attack surface.
- A vulnerability in any transitive dependency (Netty, Jackson, Spring Framework itself) could go undetected until exploited.

### Mitigation (Implemented)

1. **OWASP Dependency-Check Gradle plugin** (`org.owasp.dependencycheck` version 12.1.0) integrated into `build.gradle.kts:7`.

2. Configured to **fail the build** when CVSS 9.0+ (critical) vulnerabilities are found, with HTML and JSON reports generated.

3. Unused dependencies removed: `bucket4j-core`, `resilience4j-circuitbreaker`, `resilience4j-retry`, `resilience4j-reactor`, `spring-retry`, `spring-aspects`.

4. A suppression file (`config/dependency-check-suppressions.xml`) is included for managing false positives.

### Analysis & Prioritization

**Severity: Medium.**
**Priority: 2.**

The application does not process user files or execute user-supplied code, so remote code execution in a library is less likely to be exploitable here. However:

- A vulnerability in a networking library (Netty, Reactor Netty) could allow an attacker to intercept or redirect traffic, potentially leaking the OpenAI API key.
- A deserialization vulnerability in Jackson could be triggered via crafted API responses.
- Without automated scanning, vulnerable dependencies can be introduced silently in any PR and deployed to production.

The risk is elevated by the application's exposure to external input: the OpenAI API returns JSON that is deserialized by Jackson, and user prompts are forwarded to the upstream API. A compromised dependency in the deserialization or HTTP client layer would be catastrophic.

Integrating OWASP Dependency-Check into CI ensures that every PR is scanned before reaching production. Setting the fail threshold at CVSS 9.0+ prevents critical vulnerabilities from being deployed while giving the team time to address lower-severity issues.

---

## Vulnerability 3 -- A07: Identification and Authentication Failures

### OWASP Category

**A07:2021 - Identification and Authentication Failures**

### Identification

**Location:** Not applicable (deferred)

In a publicly facing API, callers should be authenticated to prevent abuse of the API key. Without caller identification you cannot:

- Rate-limit per tenant.
- Audit who used the service and when.
- Revoke access for a specific abuser without rotating the shared key.
- Implement per-tenant usage quotas.
- Distinguish between legitimate traffic and abuse in logs.

### Recommendation (Not Implemented)

For a public-facing deployment, JWT-based authentication is recommended:

- A `JwtTokenProvider` would issue and validate HMAC-SHA256 signed JWTs, configurable via a secret and expiration.
- A `JwtAuthFilter` would intercept requests, validate `Authorization: Bearer <token>` headers, and inject caller identity for audit logging.

### Analysis & Prioritization

**Severity: High.**
**Priority: Deferred.**

This application is **not a public-facing service**. It is an internal development tool used by a small, known set of developers. Leaving it unauthenticated simplifies testing and development iteration. If the application is ever deployed to a public or multi-tenant environment, JWT-based caller authentication should be added before that deployment.

---

## Summary of Remediations

| OWASP | Vulnerability                       | Severity | Status          | Implementation                                                                        |
| ----- | ----------------------------------- | -------- | --------------- | ------------------------------------------------------------------------------------- |
| A01   | Broken Access Control -- open proxy | Critical | Not implemented | Internal dev tool; all endpoints open for browser access                              |
| A06   | Vulnerable Components -- no SCA     | Medium   | Fixed           | OWASP Dependency-Check plugin in Gradle; CI fails on CVSS >= 9.0; unused deps removed |
| A07   | Auth Failures -- no caller identity | High     | Not implemented | Recommended for public API; left unauthenticated for internal dev use                 |

---

## Residual Risks

The following risks were identified but **not addressed** in this round, along with the rationale:

| Risk                                           | Rationale                                                                                                                                                                   |
| ---------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **No access control or caller authentication** | All endpoints are fully open. This is acceptable for an internal dev tool but must be addressed before any public or production deployment.                                 |
| **No rate limiting**                           | A single user could send enough requests to exhaust the OpenAI budget. Rate limiting should be added in production.                                                         |
| **No IP allowlisting**                         | The API is accessible from any IP. For internal services, restricting by source IP range would add a defense layer.                                                         |
| **Vault over HTTP**                            | The Vault configuration uses `scheme: http`. The OpenAI API key and Vault token are transmitted in cleartext if Vault is not on localhost. Should use HTTPS in production.  |
| **Actuator exposure**                          | All Actuator endpoints (including `/actuator/env`, `/actuator/metrics`) are accessible without restriction. They should be explicitly restricted or disabled in production. |
| **No audit log persistence**                   | API requests are not persistently logged. A production deployment should log all API requests to an audit trail.                                                            |

---

## Conclusion

A06 (Vulnerable Components) is the only vulnerability actively addressed in this round. A01 (Broken Access Control) and A07 (Caller Authentication) were reviewed but deferred -- this is an internal development tool where ease of testing takes priority over access restrictions.

The application is now protected by:

- **Supply chain**: Automated dependency scanning catches vulnerable libraries before deployment.

The most impactful residual risks are the absence of access control and rate limiting, both of which must be addressed before any public or production deployment.
