# Marquee

Marquee is a local-only video streaming backend. An admin uploads videos; they are transcoded to
adaptive-bitrate HLS and streamed to viewers with signed URLs. The Phase 1 stack is the Spring Boot
API and transcoder, PostgreSQL, Redis, RabbitMQ and MinIO. See [spec.md](spec.md) for the full
design and [docs/phase-1-notes.md](docs/phase-1-notes.md) for the decisions made in Phase 1.

## Start the local stack

1. Copy `.env.example` to `.env` and replace its local passwords and secrets.
2. Start Docker Desktop (or another Docker Compose provider).
3. From the project root, run:

   ```sh
   docker compose up --build
   ```

> **Folder name:** Docker's BuildKit rejects build paths with non-ASCII characters, such as the em
> dash (—) in this folder's name (`header key "x-docker-expose-session-sharedkey" contains value
> with non-printable ASCII characters`). Rename the folder, or build through an ASCII symlink:
>
> ```sh
> ln -s "$PWD" ~/marquee && cd ~/marquee && docker compose -p marquee up --build
> ```

Compose waits for the infrastructure health checks and creates the MinIO `marquee` bucket before
starting the API and transcoder. On start-up the API applies the Flyway migrations and, with
`SEED_ENABLED=true` (the default), creates the seed data:

| Account | Email (default) | Password | Profiles |
|---|---|---|---|
| Admin | `admin@marquee.local` | `SEED_ADMIN_PASSWORD` | Admin |
| Viewer | `viewer@marquee.local` | `SEED_USER_PASSWORD` | Viewer, Kids (kids profile) |

It also creates six genres. Seeding is safe to run on every start.

| Service | URL |
|---|---|
| API health | http://localhost:8080/actuator/health |
| API docs (Swagger UI) | http://localhost:8080/swagger-ui.html |
| Transcoder health | http://localhost:8081/actuator/health |
| RabbitMQ management | http://localhost:15672 |
| MinIO console | http://localhost:9001 |

Stop the stack with `docker compose down`. Postgres and MinIO data persist in the `postgres-data`
and `minio-data` volumes; use `docker compose down -v` to start fresh.

## Ingest a sample video end to end

With the stack running:

```sh
scripts/ingest-sample.sh                 # downloads a 10 s Big Buck Bunny clip (CC BY 3.0)
scripts/ingest-sample.sh path/to/video.mp4
```

The script needs `curl` and `jq`. It runs these steps:

1. Creates a title.
2. Uploads the video straight to MinIO through a presigned URL.
3. Completes the upload, which queues the transcode job.
4. Waits until the asset is `READY`.
5. Publishes the title.
6. Requests playback as the seeded viewer.

It fails unless the master playlist has at least two variants and a stream request without a token
is rejected. At the end it prints a manifest URL you can open in VLC (Media > Open Network Stream)
or Safari.

## API overview

All `/api` calls except register, login and refresh need `Authorization: Bearer <accessToken>`.
Viewer endpoints are scoped to a profile with `X-Profile-Id: <profileId>`. Errors are RFC 7807
`application/problem+json`.

- Auth: `POST /api/auth/register | login | refresh | logout`
- Profiles: `GET/POST /api/profiles`, `PUT/DELETE /api/profiles/{id}`
- Browse: `GET /api/home`, `GET /api/titles/{id}`, `GET /api/titles?genre=&type=&page=&size=`,
  `GET /api/search?q=`, `GET /api/series/{titleId}/next-episode`
- Lists and ratings: `PUT/DELETE /api/my-list/{titleId}`, `PUT /api/ratings/{titleId}` with `{"value": 1 | -1}`
- Playback: `GET /api/playback/{assetId}`, `PUT /api/profiles/{profileId}/progress/{assetId}`,
  `GET /stream/{assetId}/...?token=`
- Admin (ADMIN role): titles, seasons, episodes, genres, `POST /api/admin/titles/{id}/images`,
  `POST /api/admin/assets`, `POST /api/admin/assets/{id}/complete`, `GET /api/admin/assets/{id}`

The full, browsable reference is in Swagger UI.

## Build and test

Requires Java 21 and Maven, plus Docker for the integration tests.

```sh
mvn verify
```

This runs unit tests and the Testcontainers integration tests (`*IT`) against Postgres, RabbitMQ
and MinIO. The transcoder's `TranscodePipelineIT` also needs `ffmpeg` and `ffprobe` on the `PATH`
(`brew install ffmpeg`); without them it is skipped.

> If the project folder is synced by iCloud Drive (for example on `~/Desktop`), iCloud can create
> `"* 2.class"` conflict copies in `target/` and break builds. Run `mvn clean verify`, or move the
> project out of the synced folder.
