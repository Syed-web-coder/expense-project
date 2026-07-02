# Local Dev Live-Reload Loop

## Prerequisites
- Docker Desktop running
- `uptimecrew/expense-api:0.1.0` image built locally

## Starting the stack

```bash
make up
```

## Live-reload development (hot restart in < 3s)

**Terminal 1** — continuous Gradle build on the host:
```bash
./gradlew bootJar --continuous
```

**Terminal 2** — start the dev override service:
```bash
docker compose --profile dev up -d expense-api-dev
docker compose --profile dev logs -f expense-api-dev
```

Edit any Java file → Gradle rebuilds the JAR → the JVM inside the container restarts automatically in < 3s.

The dev service runs on port 8081 (not 8080) so it doesn't conflict with the main expense-api container.

## Verify profiles

```bash
# Lists expense-api-dev
docker compose --profile dev config --services

# Lists seed-fixtures
docker compose --profile test config --services
```

## Tear down

```bash
make down
```
