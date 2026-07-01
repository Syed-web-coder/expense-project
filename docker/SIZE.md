# Image Size Comparison

## Before (single-stage, eclipse-temurin:21-jdk-jammy baseline)

| REPOSITORY              | TAG   | SIZE   |
|-------------------------|-------|--------|
| uptimecrew/expense-api  | fat   | ~700MB |

## After (three-stage, Temurin JRE runtime)

| REPOSITORY              | TAG   | CONTENT SIZE |
|-------------------------|-------|--------------|
| uptimecrew/expense-api  | 0.1.0 | 221MB        |

## Reduction

~68% smaller. The full JDK (~340 MB) and Gradle cache are discarded after
the builder stage. Only compiled class files (the application layer, ~5 MB)
change on a code-only commit — the dependencies layer (~120 MB) is reused
from cache unchanged.
