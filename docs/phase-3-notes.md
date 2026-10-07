# Phase 3 notes — Recommendations

Marquee reuses the existing real-time recommendation system (recsys, `~/Desktop/Recommendation
engine`), as the spec requires, rather than building its own. These notes record how it is
integrated and why.

## A dedicated recsys instance

- `scripts/recsys.sh` runs recsys's own `docker-compose.yml` as a separate Compose project
  (`marquee-recsys`), with `recsys/compose.marquee.yml` layered on top. Nothing in the recsys
  checkout is modified.
- **Why a separate instance:** recsys has no per-tenant item filter. A shared instance loaded
  with its synthetic catalog (10k videos) would mostly recommend items Marquee doesn't have. The
  dedicated instance's catalog contains only Marquee titles.
- **Ports:** recsys's defaults (8080, 8081, 3000, 5432, 6379) collide with Marquee. The overlay
  publishes only the three APIs Marquee calls (18080 recommendations, 18081 ingest, 18082 catalog)
  and keeps its infrastructure internal.
- **Trimmed:** only the services Marquee needs are started. LLM enrichment and the
  Prometheus/Grafana/Jaeger/Alertmanager stack are left out, so both stacks fit in Docker's
  memory: recsys uses about 2.5 GB.
- `recsys/recsys.env` is passed as the env file, so the recsys checkout's own `.env` (state of
  its standalone stack) doesn't leak in. The instance uses its own API key.
- The Marquee API container reaches recsys through `host.docker.internal`, because the two stacks
  are on separate Compose networks.

## Identity mapping

- recsys user = **profile** (`mq-p-{profileId}`). Each profile has its own taste, and kids
  profiles stay separate.
- recsys item = **title** (`mq-t-{titleId}`). A series is one item, whichever episode was
  watched. All items are in the `video` domain.

## Engagement events

Events are recorded in a **transactional outbox** (`recsys_outbox`, migration `V5`) in the same
transaction as the change they describe. `RecsysOutboxRelay` delivers them every 2 s:
- Catalog changes go first, so items exist before events about them.
- Delivered rows are deleted; failed rows stay and are retried.
- A 4xx response (the payload itself is invalid) is dropped and logged.
- Events older than 24 h are purged, because recsys treats them as late (offline only).
- `FOR UPDATE SKIP LOCKED` lets several API instances relay safely, and recsys dedupes on the
  UUIDv7 event id.

Mapping from the spec's events to recsys event types. recsys scores one `PLAY_END` per viewing by
completion ratio (for video: ≥ 90% strong, ≥ 25% meaningful, < 10% abandon), so a full viewing must
not produce several `PLAY_END`s:

| Marquee | recsys | Source |
|---|---|---|
| play_start | `PLAY_START` | `GET /api/playback/{assetId}` |
| progress_25 | `PLAY_END` at 25% (a meaningful view, even if the viewer stops) | progress heartbeat |
| progress_50 / progress_75 | `DWELL` (recorded; no taste weight for video) | progress heartbeat |
| completed | `PLAY_END` at 100% | progress heartbeat (≥ 95%) |
| thumbs_up / thumbs_down | `LIKE` / `DISLIKE` (only when the rating changes) | `PUT /api/ratings/{id}` |
| my_list_add | `SAVE` (only when newly added) | `PUT /api/my-list/{id}` |
| search | `SEARCH`, with a random query id and **no query text** (a recsys rule) | first page of `GET /api/search` |

- Milestones are reported once per viewing, tracked in `watch_progress.milestone`. Seeking back
  doesn't repeat them, a jump forward reports every milestone it passes, and restarting a finished
  title from the start begins a new viewing.
- **Catalog:** creating, updating, publishing or deleting a title, and a movie becoming playable,
  queue a catalog sync. The relay sends the title's current state, or deletes the item when the
  title is unpublished or gone. On startup, every published title is queued again (upserts are
  idempotent).
- **Right to erasure:** deleting a profile calls recsys's user-data deletion.

## Home rows

The rows, in order: Continue Watching, **Top picks for {profile}**, **Because you watched X**,
My List, Trending, New Releases, then genre rows.

- **Top picks:** recsys `home` surface.
- **Because you watched X:** recsys `related` surface, seeded with the most recently watched
  title. In a catalog this small, recsys's item-to-item signals (co-engagement, next item) are
  sparse, so this mostly reflects the profile's recent taste. The row is left out when it has
  nothing to show.
- **Hydration:** results are hydrated from Marquee's own catalog. This drops ids Marquee doesn't
  know (recsys pads short lists with a bundled static fallback of synthetic ids), keeps recsys's
  order, and applies Marquee's visibility rules (published only, kids ratings). Kids profiles also
  request `explicit=false`.
- **Failure handling:** calls have a 300 ms connect and 800 ms read timeout and run in parallel,
  behind a Resilience4j circuit breaker (`recsys-serving`).
  - The breaker opens after half of the last 10 calls fail (minimum 5), then tries again after
    30 s. 4xx responses don't count.
  - Outbox delivery has its own breaker (`recsys-delivery`), so an ingest backlog can't trip the
    home page.
  - When recsys fails, is open, or returns nothing usable, **Top picks falls back to Trending** and
    "Because you watched" is omitted.
- **Measured:** with the recommendation API stopped, `/api/home` answered in 36 ms with Top picks
  served from Trending.

## Testing

- **Unit tests:** milestone logic, event mapping, UUIDv7, row hydration and fallback.
  `HttpRecsysClientTest` runs the real HTTP client against an in-process HTTP server: request
  shape, API key, ISO timestamps, timeouts opening the breaker, an open breaker short-circuiting,
  and 4xx responses not tripping it.
  - This found that Spring's JDK client throws `CancellationException` on a read timeout, not a
    `RestClientException`; it is now handled.
- **`RecsysIntegrationIT`** (Postgres, stub recsys client):
  - milestones and play start recorded once and delivered
  - thumbs, My List and search events, with no query text
  - outbox retained while recsys is down and delivered afterwards; permanently rejected batches
    dropped
  - catalog upsert and delete
  - home rows hydrated and seeded correctly
  - home falls back to Trending when recsys is down
  - profile deletion triggers erasure
- **`e2e/recommendations.spec.ts`** is the Phase 3 acceptance test, against the real recsys:
  - It uses a new profile with no history, so Top picks start as the Trending fallback.
  - It watches two of four documentaries, then waits until Top picks lead with the two unwatched
    documentaries. Measured: before "Kelp Forest Secrets, Coral Kingdom"; after "Deep Ocean
    Giants, Reef Night Shift", then the comedies.
  - It checks the same rows in the web app.
  - It then stops the recsys recommendation API and confirms the home page (API and UI) still
    renders, with Top picks from Trending, before restarting it.
  - It is skipped when recsys isn't running.

## Left for later

- The fixture titles used by the end-to-end tests (four documentaries and four comedies) stay in
  the local catalog between runs.
- recsys's LLM "why this" explanations aren't requested or shown. The enrichment worker isn't
  started in the trimmed instance.
- Recommendation attribution (recsys `recommendationId` / position on later events) isn't sent,
  so recsys can't measure Marquee's click-through on recommendations.
