# syntax=docker/dockerfile:1.7
#
# Three-stage build: builder + extractor + distroless runtime.
# Build:
#   docker build \
#     --build-arg APP_VERSION=0.1.0 \
#     --build-arg GIT_SHA=$(git rev-parse --short HEAD) \
#     -t uptimecrew/expense-api:0.1.0 .

# -------- 1. BUILD STAGE --------
FROM eclipse-temurin:21-jdk-jammy@sha256:9d8dcf999b0bce2453e913823595a5ff2a4e8e9e5d5241b45280d0ff069818ec AS builder
WORKDIR /workspace

COPY gradlew gradlew.bat ./
COPY gradle/ gradle/
COPY build.gradle settings.gradle ./

RUN sed -i 's/\r//' gradlew && chmod +x gradlew

RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    ./gradlew --no-daemon dependencies

COPY src/ src/

RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    ./gradlew --no-daemon bootJar -x test

# -------- 2. EXTRACT STAGE --------
FROM eclipse-temurin:21-jre-jammy@sha256:d63bd8d9b171999cbed8576f2c76e874dd4856791a358536e5c4d407e77edc13 AS extractor
WORKDIR /extract
COPY --from=builder /workspace/build/libs/expense-tracking-0.1.0-SNAPSHOT.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract --destination .

# -------- 3. RUNTIME STAGE --------
FROM eclipse-temurin:21-jre-jammy@sha256:d63bd8d9b171999cbed8576f2c76e874dd4856791a358536e5c4d407e77edc13 AS runtime

ARG APP_VERSION=0.0.0
ARG GIT_SHA=unset
LABEL org.opencontainers.image.title="expense-api"
LABEL org.opencontainers.image.version="${APP_VERSION}"
LABEL org.opencontainers.image.revision="${GIT_SHA}"
LABEL org.opencontainers.image.source="https://github.com/Syed-web-coder/expense-project"
LABEL org.opencontainers.image.licenses="Apache-2.0"

USER 1000
WORKDIR /home/app

COPY --from=extractor /extract/dependencies/          ./
COPY --from=extractor /extract/spring-boot-loader/    ./
COPY --from=extractor /extract/snapshot-dependencies/ ./
COPY --from=extractor /extract/application/           ./

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
  CMD ["curl", "-f", "http://localhost:8080/actuator/health/readiness"]

ENTRYPOINT ["java","org.springframework.boot.loader.launch.JarLauncher"]
