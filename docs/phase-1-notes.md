# Phase 1 notes — backend core and ingest pipeline

Decisions made while building Phase 1, and what is deliberately left for later.

## Open decisions from the spec (§10)

- **Job queue: RabbitMQ**, as the spec suggests. All queues are on the default exchange
  (names in `marquee-common` `TranscodeQueues`):
  - `marquee.transcode`: jobs from the API.
  - `marquee.transcode.retry`: failed jobs wait here for `TRANSCODE_RETRY_DELAY_MS` (TTL), then
    dead-letter back to `marquee.transcode`.
  - `marquee.transcode.dlq`: jobs that failed all `TRANSCODE_MAX_ATTEMPTS` (3) attempts.
  - `marquee.transcode.events`: `STARTED` / `SUCCEEDED` / `FAILED` events back to the API.
- **Segments: MPEG-TS** (`seg_00000.ts`), the spec's choice for broad compatibility. Moving to
  fMP4/CMAF is the prerequisite for DRM.

## Ingest and transcoding

- **The transcoder has no database access.** It reports progress as events, and the API is the only
  writer of `video_assets` and `transcode_jobs` (one row per attempt). This keeps the transcoder
  stateless and lets it scale on its own.
- **Retries are explicit, not broker redelivery.** The listener always acks, then routes a failed
  job to the retry queue with `attempt + 1`, or to the DLQ after the last attempt. This avoids the
  default Spring AMQP requeue loop and keeps the attempt count in the message.
- **Idempotency.** `master.m3u8` is uploaded last, so if it already exists the transcoder skips the
  work and reports success. The API also refuses `complete` (409) for assets that are already
  `TRANSCODING` or `READY`.
- **The job is published after commit**, so the transcoder can never report back before the
  `TRANSCODING` status is visible.
- **Ladder and encoding:** rungs above the source height are skipped (a source below 360p still
  gets 360p). Two-second GOPs (`-g` = 2 × fps) keep keyframes aligned across renditions and on
  segment boundaries. Rate control is the spec's ladder bitrates, capped with
  `-maxrate`/`-bufsize`. The master playlist uses relative variant URIs.
- **Thumbnails** are extracted every 10 s to `thumbs/{assetId}/thumb_N.jpg` (used in Phase 4).

## Playback

- Stream tokens are HMAC-signed `assetId:profileId:exp` with a 2 h TTL. A missing, tampered,
  expired or other-asset token returns **401**. A kids profile asking for a title above its rating
  gets **403** from `/api/playback`.
- Playlists are rewritten so each child URI is an absolute `/stream/...` URL carrying the token,
  resolved against the playlist's own directory. Segments support `Range` (206 + `Content-Range`)
  and are cached as `public, immutable`. Playlists get a 60 s cache and are sent with an explicit
  `Content-Length`; a chunked playlist makes ffmpeg's HLS demuxer report an I/O error at end of
  stream.
- `resumeAt` comes from `watch_progress`, unless that progress is completed.
- **DRM would plug in** by replacing signed-token segments with encrypted CMAF segments and a
  license server. The stream proxy and token check stay as the authorization layer.

## Catalog and viewer rules

- **Visibility, applied in SQL everywhere a viewer sees titles:** only published titles, and for
  kids profiles only `G, PG, TV-Y, TV-Y7, TV-G, TV-PG`. Unrated titles are hidden from kids
  profiles, and kids cannot play them.
- **Completion** is at ≥ 95% of the duration.
- **Home rows:** Continue Watching (latest progress per title, so a series appears once at its
  latest episode), My List, Trending (distinct viewers in the last 7 days, by `watch_progress.updated_at`;
  the schema has no "started at" column), New Releases, and the top 5 genres by number of visible titles.
- **Search** uses Postgres full-text search on `search_vector` (name weighted above synopsis,
  maintained by a trigger, `V4`), plus a substring match on the name so partial words still hit.
- **Errors** are RFC 7807 `application/problem+json`. Unauthenticated API calls get 401.

## Schema changes

- `V3`: the native Postgres enum columns became `VARCHAR` with `CHECK` constraints. JPA binds enums
  and the maturity-rating converter as strings, which native enum columns reject.
- `V4`: trigger to maintain `titles.search_vector`.

## Testing

- `mvn verify` runs unit tests (Surefire) and integration tests (`*IT`, Failsafe) against
  Testcontainers.
- Upload → READY is covered in two halves that share the `marquee-common` message contract:
  - `TranscodePipelineIT` (transcoder): RabbitMQ + MinIO + local ffmpeg. A generated 5-second
    clip becomes 2-variant HLS with thumbnails, and a failing job is retried and then dead-lettered.
    It is skipped when ffmpeg is not on the `PATH`.
  - `IngestPipelineIT` (API): Postgres + RabbitMQ + MinIO. Presigned upload, job published,
    events applied, asset `READY`.
  - The whole chain runs against the real Compose stack with `scripts/ingest-sample.sh`.
- `PlaybackAuthIT` covers signed-token streaming, playlist rewriting, ranges and the 401/403 cases.
- Testcontainers is pinned to 1.21.4, the minimum version that works with Docker Engine 29.

## Seed data

`DevDataSeeder` runs when `SEED_ENABLED=true` (the Compose default). It creates an admin, a viewer
with an adult and a kids profile, and 6 genres. Passwords come from `.env`. It is safe to run on
every start.

## Left for later

- **Images:** `posterKey`/`backdropKey` are object keys in a private bucket. Phase 2 needs a way to
  serve them (signed GET URLs or an image proxy).
- **Redis** runs in Compose but is unused. Progress writes go straight to Postgres, which is fine
  at this scale; the spec's Redis debounce is optional.
- **No full-stack test in Testcontainers.** No automated test runs the API and transcoder
  containers together; `ingest-sample.sh` is that check.
