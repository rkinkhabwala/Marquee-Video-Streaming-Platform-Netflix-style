# Marquee — Video Streaming Platform (Netflix-style)

> Working name: **Marquee**. Rename freely.
> Build target: local-only (Docker Compose), no cloud deploy.
> Implementation: Claude Code in VS Code, phase by phase. Do not start a phase until the previous phase's acceptance criteria pass.

---

## 1. Goal

A Netflix-style streaming app where an admin uploads movies and series episodes, the system transcodes them into adaptive-bitrate HLS, and users browse a catalog, pick a profile, and watch with resume-where-you-left-off. The interesting engineering is the **ingest → transcode → adaptive playback** pipeline, not the CRUD.

### Non-goals (v1)
- DRM (Widevine/FairPlay) — out of scope; note where it would plug in.
- Payments/subscriptions, live streaming, downloads/offline, mobile apps.
- User-generated uploads (admin-only uploads, same as Cadence).

---

## 2. Tech stack

| Concern | Choice |
|---|---|
| Language / build | Java 21, Maven multi-module |
| Framework | Spring Boot 3.x (Web, Security, Data JPA, Validation, AMQP, Actuator) |
| Database | PostgreSQL 16 + Flyway migrations |
| Object storage | MinIO (S3-compatible), via AWS SDK v2 |
| Job queue | RabbitMQ (transcode jobs) |
| Transcoding | FFmpeg / ffprobe (invoked via ProcessBuilder in worker container) |
| Cache | Redis (session-ish data, title-row caching, rate limiting) |
| Auth | Spring Security + JWT (access + refresh tokens), BCrypt |
| Search | PostgreSQL full-text search (tsvector) in v1 |
| Frontend (Phase 2) | React + Vite + TypeScript, hls.js for playback, TanStack Query |
| Testing | JUnit 5, Mockito, Testcontainers (Postgres, MinIO, RabbitMQ), RestAssured |
| Local infra | Docker Compose |

---

## 3. Architecture

```
                ┌──────────────┐
  Browser ─────▶│  marquee-api │──── PostgreSQL
 (React+hls.js) │ (Spring Boot)│──── Redis
                └──────┬───────┘
          upload src   │  publish TranscodeJob
              ▼        ▼
           MinIO ◀── RabbitMQ ──▶ ┌──────────────────┐
     (raw/ + hls/)                │ marquee-transcoder│ (Spring Boot + FFmpeg)
              ▲                   └─────────┬────────┘
              └──── writes HLS renditions ──┘
```

### Maven modules
- `marquee-common` — shared DTOs, job message schemas, storage key conventions.
- `marquee-api` — REST API: auth, profiles, catalog, upload, playback, watch progress, search.
- `marquee-transcoder` — RabbitMQ consumer that runs FFmpeg and writes HLS output to MinIO.
- `marquee-web` (Phase 2) — React app (not a Maven module; sibling folder `web/`).

### Storage layout (MinIO bucket `marquee`)
```
raw/{assetId}/source.{ext}
hls/{assetId}/master.m3u8
hls/{assetId}/{1080p|720p|480p|360p}/index.m3u8
hls/{assetId}/{rendition}/seg_00001.ts
images/{titleId}/poster.jpg | backdrop.jpg
thumbs/{assetId}/thumb_{n}.jpg
```

---

## 4. Domain model

```
users(id, email UNIQUE, password_hash, role[USER|ADMIN], created_at)
profiles(id, user_id FK, name, avatar_key, is_kids, created_at)   -- max 5 per user
titles(id, type[MOVIE|SERIES], name, synopsis, release_year, maturity_rating,
       poster_key, backdrop_key, search_vector tsvector, published bool, created_at)
genres(id, name UNIQUE)
title_genres(title_id, genre_id)
seasons(id, title_id FK, season_number, name)
episodes(id, season_id FK, episode_number, name, synopsis, video_asset_id FK)
video_assets(id, title_id FK NULL, status[UPLOADED|TRANSCODING|READY|FAILED],
             duration_seconds, source_key, master_playlist_key, error_message, created_at)
transcode_jobs(id, video_asset_id FK, attempt, status, started_at, finished_at, log_tail)
watch_progress(profile_id, video_asset_id, position_seconds, duration_seconds,
               completed bool, updated_at, PK(profile_id, video_asset_id))
my_list(profile_id, title_id, added_at, PK(profile_id, title_id))
ratings(profile_id, title_id, value[-1|1], PK(profile_id, title_id))   -- thumbs up/down
```

Rules:
- A MOVIE has exactly one `video_asset` linked via `titles` → `video_assets.title_id`.
- A SERIES has seasons → episodes, each episode has its own `video_asset`.
- `is_kids` profiles only see titles with maturity_rating in (G, PG, TV-Y, TV-Y7, TV-G, TV-PG).
- `watch_progress.completed = true` when position ≥ 95% of duration.

---

## 5. Ingest & transcoding pipeline (core of the project)

1. Admin creates title metadata (`POST /api/admin/titles`).
2. Admin requests upload: `POST /api/admin/assets` → API creates `video_asset` (UPLOADED) and returns a **presigned MinIO PUT URL** for `raw/{assetId}/source.mp4`. Large files go straight to MinIO, never through the API.
3. Admin calls `POST /api/admin/assets/{id}/complete` → API verifies object exists, sets status TRANSCODING, publishes `TranscodeJob{assetId, sourceKey}` to RabbitMQ.
4. Transcoder:
   - Downloads source to a temp dir; runs `ffprobe` for duration/resolution.
   - Builds the rendition ladder, skipping rungs above the source height:

     | Rendition | Resolution | Video bitrate | Audio |
     |---|---|---|---|
     | 1080p | 1920×1080 | 5000k | AAC 128k |
     | 720p | 1280×720 | 2800k | AAC 128k |
     | 480p | 854×480 | 1400k | AAC 96k |
     | 360p | 640×360 | 800k | AAC 96k |

   - H.264 (`libx264`, `-preset veryfast`), keyframes aligned (`-g 48 -keyint_min 48 -sc_threshold 0` at 24fps; compute from source fps), 6-second HLS segments, `-hls_playlist_type vod`.
   - Generates `master.m3u8` with `BANDWIDTH`/`RESOLUTION` per variant.
   - Extracts thumbnails every 10s (for scrubbing previews, used in Phase 4).
   - Uploads everything to `hls/{assetId}/...`, then reports back.
5. Result: transcoder updates `video_assets` to READY (with duration, master key) or FAILED (with error), and writes a `transcode_jobs` row.
   - Retries: up to 3 attempts with RabbitMQ dead-letter queue + delayed retry. After 3, FAILED.
   - Idempotency: if `hls/{assetId}/master.m3u8` already exists and status is READY, ack and skip.
6. Admin can poll `GET /api/admin/assets/{id}` for status.

---

## 6. Playback & secure streaming

- `GET /api/playback/{assetId}` (authenticated, profile-scoped) returns:
  ```json
  { "manifestUrl": "/stream/{assetId}/master.m3u8?token=...", "resumeAt": 1325, "durationSeconds": 6012 }
  ```
- `token` = short-lived (e.g. 2h) HMAC-signed token encoding `assetId`, `profileId`, `exp`.
- `GET /stream/{assetId}/{path}` — validates the token, streams the object from MinIO with correct `Content-Type` (`application/vnd.apple.mpegurl`, `video/mp2t`). For `.m3u8` files, **rewrites** child URIs to append the same token so segment requests stay authorized.
- Support HTTP `Range` requests and set `Cache-Control` (long for segments, short for playlists).
- Kids profiles cannot get playback for out-of-rating titles (403).
- Where DRM would go: replace signed-token stream with encrypted segments + license server. Document only.

### Watch progress
- `PUT /api/profiles/{profileId}/progress/{assetId}` with `{positionSeconds}` — client sends every 10s and on pause/exit. Upsert; cheap write.
- Debounce writes server-side through Redis if needed; flush to Postgres.

---

## 7. REST API (v1)

### Auth
- `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/refresh`, `POST /api/auth/logout`

### Profiles
- `GET/POST /api/profiles`, `PUT/DELETE /api/profiles/{id}` (max 5 per user)

### Catalog (profile-scoped via `X-Profile-Id` header)
- `GET /api/home` — rows for the home screen:
  - Continue Watching (in-progress, not completed, most recent first)
  - My List
  - Trending (most watch_progress starts in last 7 days)
  - New Releases
  - One row per top genre
- `GET /api/titles/{id}` — detail incl. seasons/episodes, in My List?, rating
- `GET /api/titles?genre=&type=&page=&size=`
- `GET /api/search?q=` — Postgres full-text on name + synopsis
- `GET /api/series/{titleId}/next-episode` — next episode after last watched (for "Play next")

### Lists & ratings
- `PUT/DELETE /api/my-list/{titleId}`
- `PUT /api/ratings/{titleId}` `{value: 1 | -1}`

### Playback
- `GET /api/playback/{assetId}`, `PUT /api/profiles/{profileId}/progress/{assetId}`, `GET /stream/...`

### Admin (role ADMIN)
- `POST/PUT/DELETE /api/admin/titles`, `POST /api/admin/titles/{id}/images` (presigned)
- `POST /api/admin/titles/{id}/seasons`, `POST /api/admin/seasons/{id}/episodes`
- `POST /api/admin/assets`, `POST /api/admin/assets/{id}/complete`, `GET /api/admin/assets/{id}`
- `POST /api/admin/titles/{id}/publish`

Conventions: RFC 7807 problem+json errors, pagination `page/size` with `totalElements`, OpenAPI via springdoc at `/swagger-ui`.

---

## 8. Phases

### Phase 1 — Backend core + ingest pipeline
- Docker Compose: postgres, redis, rabbitmq, minio (+ bucket bootstrap), api, transcoder.
- Flyway migrations for the full schema.
- Auth, profiles, admin title/season/episode CRUD, presigned upload, transcode pipeline, playback + signed stream endpoint, watch progress, home rows, search.
- Seed script: 1 admin, 1 user, 2 profiles (one kids), ~6 genres; a `scripts/ingest-sample.sh` that uploads a public-domain/Creative Commons clip (e.g. Big Buck Bunny) end to end.
- **Acceptance:**
  - `docker compose up` brings everything up healthy.
  - Running `ingest-sample.sh` ends with asset READY and a master playlist containing ≥2 variants.
  - The manifest URL from `/api/playback/{id}` plays in VLC or Safari, and fails with 401/403 when the token is missing/expired.
  - Progress saved → `/api/home` shows the title in Continue Watching with the right resume point.
  - Integration tests with Testcontainers cover upload→READY (using a 5-second test clip) and playback auth.

### Phase 2 — React web client
- Profile picker ("Who's watching?"), home with horizontally scrolling rows, hero banner, title detail modal with episodes by season, My List, thumbs up/down, search page.
- Player page with hls.js: quality auto/manual selector, resume from `resumeAt`, progress heartbeat every 10s, "Next episode" countdown at end of an episode, keyboard shortcuts (space, ←/→ 10s, F).
- Admin page: create title, upload video with progress bar, show transcode status.
- **Acceptance:** a user can log in, pick a profile, play a title, close the tab, return, and resume within ±10s; quality switches visibly when throttling network in DevTools.

### Phase 3 — Recommendations
- Reuse the existing real-time recommendation system (recsys) instead of building a new one.
- API publishes engagement events (play_start, progress_25/50/75, completed, thumbs_up/down, my_list_add, search) to the recsys ingest endpoint/queue.
- New home rows: "Because you watched X", "Top picks for {profile}". Fallback to Trending if recsys is unavailable (circuit breaker via Resilience4j).
- **Acceptance:** after watching two titles in one genre, that profile's "Top picks" changes; killing recsys doesn't break the home page.

### Phase 4 — Hardening (optional, pick and choose)
- Nginx in front of `/stream` as a local "CDN" edge cache; measure cache hit ratio.
- WebVTT subtitles upload + player track selection.
- Scrubbing preview thumbnails (sprite sheet + WebVTT thumbnails track).
- Observability: Micrometer → Prometheus + Grafana dashboard (transcode duration, queue depth, stream bytes served, p95 API latency).
- Rate limiting per user on stream endpoint (Redis token bucket); concurrent-stream limit per account (e.g. 2).

---

## 9. Engineering guidelines for Claude Code
- Package by feature (`auth`, `profile`, `catalog`, `ingest`, `playback`, `progress`), not by layer.
- Controllers thin; logic in services; DTOs as Java records; MapStruct optional.
- No large files through the API — always presigned URLs to MinIO.
- FFmpeg commands built in one class (`FfmpegCommandBuilder`) with unit tests asserting the exact argument list.
- Every external call (MinIO, RabbitMQ, recsys) behind an interface so tests can stub it.
- Config via `application.yml` + env vars; no secrets committed (`.env.example` only).
- Each phase ends with: tests green, README updated with run instructions, and a short `docs/phase-N-notes.md` of decisions made.

## 10. Open decisions (confirm before or during Phase 1)
- RabbitMQ vs a simple Postgres-backed job table for transcode jobs (RabbitMQ chosen to practice DLQ/retry patterns).
- Segment format: MPEG-TS (`.ts`, broadest compatibility, chosen) vs fMP4/CMAF (needed later for DRM).
- Whether to add a separate `marquee-gateway` (Spring Cloud Gateway) — skipped for v1 to keep the system small.