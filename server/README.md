# Asset Management Server

Java 25 / Spring Boot 4.1 multi-module backend. The API is a modular monolith;
the MQTT ingestion worker is a separate process that shares the same domain and
persistence model.

## Modules

- `platform-core`: IAM, project, MQTT, audit and shared domain/persistence code.
- `platform-api`: REST process, Spring Security, OpenAPI and Flyway migrations.
- `mqtt-worker`: independently runnable MQTT ingestion process. Topics are
  configuration data and are never compiled into the worker.

The worker includes the Apache-2.0-licensed HiveMQ MQTT Client library (not the
commercial HiveMQ broker). The adapter implementation will use it for MQTT
3.1.1/5.0, TLS and reconnect support.

## Prerequisites

- JDK 25
- Maven 3.9.9+
- PostgreSQL 18 with PostGIS
- Redis
- An MQTT 3.1.1 or 5.0 broker when device ingestion is enabled

Do not commit credentials. Runtime secrets must be supplied through environment
variables or the deployment secret manager. MQTT credentials stored in the
database are encrypted envelopes; plaintext credential columns do not exist.

## Build and test

```bash
cd server
mvn clean verify
```

## Run the API

```bash
export DB_URL='jdbc:postgresql://localhost:5432/asset_management'
export DB_USERNAME='asset_app'
export DB_PASSWORD='set-outside-source-control'
export REDIS_HOST='localhost'
export REDIS_PASSWORD='set-outside-source-control'
mvn -pl platform-api -am spring-boot:run -Dspring-boot.run.profiles=dev
```

Only `/actuator/health` is anonymous in the initial security skeleton. All
business endpoints fail closed until real authentication is implemented.

## Run the MQTT worker

```bash
export DB_URL='jdbc:postgresql://localhost:5432/asset_management'
export DB_USERNAME='asset_app'
export DB_PASSWORD='set-outside-source-control'
export REDIS_HOST='localhost'
export REDIS_PASSWORD='set-outside-source-control'
mvn -pl mqtt-worker -am spring-boot:run -Dspring-boot.run.profiles=dev
```

The initial worker starts independently but does not connect to a broker until
an adapter for the `MqttGateway` port and encrypted runtime connection loading
are implemented. It intentionally contains no default Topic.

## Project isolation

Every project-owned row has a `project_id` or an explicit project grant. The
initial migration enables PostgreSQL row-level security for membership, MQTT
routes/grants and audit rows. Authenticated transactions must set these local
settings before accessing those tables:

```sql
SET LOCAL app.current_project_id = '<project-uuid>';
SET LOCAL app.is_platform_admin = 'false';
```

Production should use separate migration-owner and runtime database roles so
the runtime role cannot bypass row-level security by owning the tables.
