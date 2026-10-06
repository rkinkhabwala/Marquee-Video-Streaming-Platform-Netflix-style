# Marquee

Marquee is a local-only video streaming backend. The Phase 1 stack consists of
the Spring Boot API and transcoder, PostgreSQL, Redis, RabbitMQ, and MinIO.

## Start the local stack

1. Copy `.env.example` to `.env` and replace its local passwords.
2. Start Docker Desktop (or another Docker Compose provider).
3. From the project root, run:

   ```sh
   docker compose up --build
   ```

Compose waits for the infrastructure health checks and bootstraps the MinIO
`marquee` bucket before starting the API and transcoder.

The API health endpoint is `http://localhost:8080/actuator/health`; the
transcoder health endpoint is `http://localhost:8081/actuator/health`.
RabbitMQ management is available at `http://localhost:15672`, and the MinIO
console at `http://localhost:9001`.

Stop the stack with `docker compose down`. Persistent local data is stored in
the `postgres-data` and `minio-data` named volumes.
