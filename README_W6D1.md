# Week 6 Day 1 — GitHub Actions CI/CD Pipeline

## What this day built

A full CI/CD pipeline for `expense-api`, spanning four tasks:

1. **`ci.yml`** — `build-test` job: checkout, Temurin 21, `./gradlew build`,
   test-report upload on failure. Triggers on PRs and pushes to `main`.
2. **`.github/actions/setup-build/action.yml`** — composite action wrapping
   checkout + `setup-java` + Gradle cache, reused by every job that compiles
   Java.
3. **`_build-and-push.yml`** — reusable workflow, called from `ci.yml` only
   on push to `main`: assumes an AWS IAM role via OIDC (no long-lived keys),
   builds the image with Buildx, runs Trivy (fails on HIGH/CRITICAL), pushes
   to ECR tagged both `<git-sha>` and `main`.
4. **`deploy-prod.yml`**, **`.github/dependabot.yml`**, **`.github/PIPELINE.md`**
   — manual prod-promotion workflow (SHA input, OIDC to a separate prod
   role, gated by required reviewers), weekly Dependabot updates for
   Actions, and full pipeline documentation.

All actions are SHA-pinned with a version comment; zero hardcoded AWS
credentials anywhere in `.github/`.

## AWS infrastructure provisioned

In account `475790160484` (`us-east-1`):
- OIDC identity provider for `token.actions.githubusercontent.com`
- `expense-api-build-push` IAM role — trust policy scoped to this repo's
  `dev` environment and `main` branch; least-privilege inline policy
  (ECR push only, scoped to the one repo)
- `expense-api-prod-deploy` IAM role — trust policy scoped to `prod`
  environment only; today's policy is just `ecr:DescribeImages`
  (placeholder pending W6D3's rollout logic)
- ECR repo `uptimecrew/expense-api` (immutable tags, scan-on-push)

## Bugs found and fixed along the way (pre-existing, not introduced today)

These were already broken on `main` before this day's work and surfaced
once the new pipeline actually exercised the full repo:

- **Broken nested-repo gitlink** at path `expense-project` — corrupted
  `git ls-tree` state from an earlier accidental `git add` inside a nested
  clone. Broke `actions/checkout` for any workflow using a local composite
  action. Fixed by removing the gitlink (`git rm --cached`).
- **Missing AWS SDK v2 dependency** — `MerchantLookupHandler.java` (W5D4
  Lambda handler) imported `software.amazon.awssdk.services.dynamodb.*`
  with no corresponding `build.gradle` dependency. 25 compile errors.
  Fixed by adding the AWS SDK v2 BOM, `dynamodb`, `aws-lambda-java-core`,
  `aws-lambda-java-events`, and `jackson-datatype-jsr310`.
- **Unset AWS region on `DynamoDbClient`** — `DynamoDbClient.create()`
  relies on the SDK's region-provider chain, which has nothing to resolve
  in CI (no `AWS_REGION` env var there, unlike real Lambda). Fixed by
  setting `Region.US_EAST_1` explicitly on the builder.
- **Three CVEs surfaced by Trivy** on the first real `main` run of
  `call-build-and-push`: `CVE-2026-22732` (CRITICAL, spring-security-web),
  `CVE-2025-41248` (spring-security-core), `CVE-2025-41249` (HIGH,
  spring-core). Fixed by bumping Spring Boot `3.4.6` → `3.4.13` (final
  patch of the 3.4.x branch), which resolves all three transitively.

## Verified vs. pending

**Verified in CI (real Ubuntu runners, real Docker):**
- `build-test` — compiles, all unit tests pass
- `setup-build` composite action
- OIDC auth + Buildx image build (succeeded on the first real `main` run)
- `actionlint` clean, zero hardcoded credentials

**Pending (blocked on repo-admin access, requested from Syed):**
- Branch protection rule on `main` requiring `build-test`
- `dev` GitHub Environment (needed for `call-build-and-push` to run without
  an "environment not found" error)
- `prod` GitHub Environment with required reviewers (needed to actually
  run `deploy-prod.yml`)
- Full `call-build-and-push` pass-through (build → Trivy pass → ECR push)
  — build and Trivy-catch both confirmed working; full green run pending
  PR #40's merge

## PRs

- **#31** (merged) — Tasks 1–4 code
- **#40** (open) — Spring Boot CVE fix, needs merge to verify the Trivy gate
  passes clean on `main`
