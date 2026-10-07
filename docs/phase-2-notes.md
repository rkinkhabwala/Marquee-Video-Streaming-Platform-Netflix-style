# Phase 2 notes — React web client

Decisions made while building the web client (`web/`), and what is left for later.

## Stack and structure

- React 19, TypeScript, Vite, React Router, TanStack Query and hls.js, as the spec asks. Plain CSS
  with custom properties; no UI framework.
- `src/api` holds the typed client and endpoints (mirroring the API's records) plus the query
  hooks. `src/player` holds the player and its hooks. `src/pages` holds one file per route, and
  `src/admin` holds the upload helpers.
- The player (hls.js) and admin routes are lazy-loaded, which keeps the main bundle at about 316 KB
  (99 KB gzipped).
- **Same origin everywhere.** In development, Vite proxies `/api`, `/stream` and `/media` to the
  API; in Compose, nginx (`Dockerfile.web`, `web/nginx.conf`) does the same. No CORS setup is
  needed. The one cross-origin call is the browser's presigned upload to MinIO, which already allows
  any origin.

## Sessions

- Tokens and the active profile are kept in `localStorage`, so a closed and reopened tab resumes
  the session. This is required by the acceptance test.
- **Refresh tokens rotate, and only the latest one is valid.** Concurrent refreshes are collapsed:
  a shared promise within a tab, and a Web Lock (`marquee-token-refresh`) across tabs. A tab that
  waited on the lock reuses the tokens the other tab stored instead of spending a stale refresh
  token. A rejected refresh signs the user out.
- The profile id is part of every viewer query key, so switching profiles never shows cached rows
  from another profile.

## Player

- hls.js where MSE is available, and native HLS on Safari, where the quality menu is hidden because
  Safari runs its own ABR.
- **Forward buffer capped at 60 s** (`maxBufferLength: 30`, `maxMaxBufferLength: 60`). By default,
  hls.js buffers `maxBufferSize ÷ bitrate`, which is about 165 s at 720p. With that, quality changes
  would take minutes to appear, and an abandoned tab would waste the over-fetched video.
- The quality label shows the rendition **on screen** (`LEVEL_SWITCHED`). After a network drop it
  changes once the already-buffered higher-quality video has played out, at most about 60 s later.
- **Progress heartbeat:**
  - Saves every 10 s while playing, and on pause, seek and end.
  - Saves with `keepalive` on `visibilitychange`/`pagehide`, and when leaving the player.
  - Never saves before playback has started, so closing the player before the resume seek lands
    can't overwrite the resume point with 0.
  - Measured: left at 23.8 s, resumed at 23.9 s.
- **Next episode:** offered 10 s before the end (or at the end for episodes under 30 s), with a
  10 s countdown. Progress is flushed first, because the API picks the episode after the most
  recently watched one.
- Keyboard shortcuts are handled in the capture phase, so the video element never also reacts to
  them.

## Admin

- Uploads go straight from the browser to MinIO using the presigned URL. XHR provides progress
  events, which fetch doesn't.
- Transcode status is polled every 3 s while any asset is `UPLOADED` or `TRANSCODING`.
- Series: add seasons and episodes, and attach a video to each episode. Artwork is uploaded through
  a presigned URL, then saved on the title.

## API additions for the client

- `GET /media/images/{titleId}/{poster|backdrop}.{jpg|png|webp}` serves artwork from the private
  bucket. It is public and cached for 1 h, and only the artwork key layout is exposed.
- `GET /api/admin/titles/{id}/assets` lists a title's assets with their transcode status.

## Testing

- **Unit tests (Vitest):**
  - API client: auth and profile headers, single-flight refresh, cross-tab token reuse, sign-out
    on a rejected refresh, problem-detail messages.
  - Progress heartbeat: no save before playback, the 10 s cadence, keepalive on close.
  - Keyboard shortcuts and quality helpers.
- **End-to-end tests (Playwright with installed Chrome)**, against the real stack and pipeline:
  - **Resume after closing the tab** (Phase 2 acceptance): within ±10 s; measured 0.1 s.
  - **Quality** (Phase 2 acceptance): manual 360p/720p selection, then Auto settles on 720p and
    drops to 360p under ~700 kbit/s throttling.
  - **Next episode:** the overlay appears at the end of S1:E1, and "Play now" opens S1:E2.
  - **Admin:** create a title, see the upload progress bar (with upload throttling), status
    becomes `Ready · 5s`, publish. The title is deleted afterwards.
  - The suite runs against the Vite dev server, and against the nginx container with
    `E2E_BASE_URL=http://localhost:3000`.

## Left for later

- Stream tokens expire after 2 h, so a single viewing longer than that would stop. Refreshing the
  token mid-playback needs an API endpoint plus an hls.js loader that swaps the token.
- Only one refresh token is valid per user (API design), so signing in on a second browser signs
  out the first.
- No profile editing or deletion UI. The API supports both.
- Ratings can be changed but not cleared, because the API has no delete endpoint for ratings.
