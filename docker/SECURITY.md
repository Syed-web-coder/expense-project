# expense-api/docker/SECURITY.md

## Base image choice

| Stage     | Base image                      | Why                                                        |
|-----------|---------------------------------|------------------------------------------------------------|
| builder   | eclipse-temurin:21-jdk-jammy    | Full JDK + Gradle; discarded after build                   |
| extractor | eclipse-temurin:21-jre-jammy    | JRE only; runs layertools extract                          |
| runtime   | eclipse-temurin:21-jre-jammy    | JRE with shell; enables curl-based HEALTHCHECK (path B)    |

**HEALTHCHECK path B trade-off**: distroless has no shell or curl. Path A ships
a static Go probe binary (~200 KB). Path B uses eclipse-temurin:21-jre-jammy for
the runtime stage, enabling curl-based health checks at the cost of a slightly
larger attack surface. Path B is acceptable for this assignment; switch to path A
before production hardening.

## Pinned digests (current)

```text
eclipse-temurin:21-jdk-jammy  @sha256:801b7e1a9c4befaf82bf9a2a58025ef43a7694bbc84779187ad0524d84742772
eclipse-temurin:21-jre-jammy  @sha256:199aebeb3adcde4910695cdebfe782ada38dadb6cc8013159b58d3724451befd
```

Refresh on the first business day of each month, or immediately when Trivy
reports a new HIGH/CRITICAL on the current digest.

## Three commands

```bash
# 1. Build
docker build \
  --build-arg APP_VERSION=0.1.0 \
  --build-arg GIT_SHA=$(git rev-parse --short HEAD) \
  -t uptimecrew/expense-api:0.1.0 .

# 2. Scan
trivy image --severity HIGH,CRITICAL --ignore-unfixed uptimecrew/expense-api:0.1.0

# 3. Run
docker run -d --name expense-api \
  --memory=512m --cpus=1.0 \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=local \
  uptimecrew/expense-api:0.1.0
```

## Scan cadence

- **Every PR**: .github/workflows/docker.yml runs Trivy with --exit-code 1.
- **On merge to main**: same scan plus push to private registry.
- **Weekly cron** (future): re-scan the deployed digest for newly-landed CVEs.

## Tagging policy

- uptimecrew/expense-api:0.1.0 — semver, immutable.
- uptimecrew/expense-api:<git-sha> — exact source pin, immutable.
- Never :latest. Deploys reference digests: uptimecrew/expense-api@sha256:...

## Trivy scan waivers (dated 2026-07-01)

Scanned with: `trivy image --severity HIGH,CRITICAL --ignore-unfixed uptimecrew/expense-api:0.1.0`

Remaining findings are upstream CVEs not yet resolved in Spring Boot 3.4.6 BOM.
Bumped from 3.4.3 → 3.4.6 which resolved the Spring Security CRITICAL CVE-2025-41232
and several Tomcat findings. Remaining items:

| CVE | Library | Fix available in | Waiver reason |
|-----|---------|-----------------|---------------|
| CVE-2026-41293 | tomcat-embed-core 10.1.41 | 10.1.55 | Requires Spring Boot 3.5.x; breaking upgrade |
| CVE-2026-22732 | spring-security-web 6.4.6 | 6.5.9 | Requires Spring Boot 3.5.x; breaking upgrade |
| CVE-2025-14813 | bcprov-jdk18on 1.78 | 1.84 | Transitive via Spring Security; no override available |
| CVE-2026-54512 | jackson-databind 2.18.4 | 2.18.8 | Awaiting Spring Boot BOM update |
| CVE-2026-35568 | mcp-core 0.17.0 | 1.0.0 | Requires Spring AI 1.1.6+ upgrade |

Review cadence: first business day of each month. Upgrade to Spring Boot 3.5.x
once Spring AI 1.1.x supports it.
