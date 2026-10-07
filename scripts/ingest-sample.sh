#!/usr/bin/env bash
# Ingests a Creative Commons sample clip end to end against the running Compose stack:
# create title -> presigned upload -> transcode -> READY -> publish -> playback URL.
#
# Usage: scripts/ingest-sample.sh [path/to/video.mp4]
# Without an argument it downloads a 10 s 720p cut of Big Buck Bunny (CC BY 3.0, Blender Foundation).
# Reads credentials from .env when present; see .env.example.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [[ -f "$ROOT/.env" ]]; then
  set -a; source "$ROOT/.env"; set +a
fi

API="${API_URL:-http://localhost:${API_PORT:-8080}}"
ADMIN_EMAIL="${SEED_ADMIN_EMAIL:-admin@marquee.local}"
ADMIN_PASSWORD="${SEED_ADMIN_PASSWORD:-admin_local_pass}"
USER_EMAIL="${SEED_USER_EMAIL:-viewer@marquee.local}"
USER_PASSWORD="${SEED_USER_PASSWORD:-viewer_local_pass}"
SAMPLE_URL="${SAMPLE_URL:-https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/720/Big_Buck_Bunny_720_10s_1MB.mp4}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-300}"

for tool in curl jq; do
  command -v "$tool" >/dev/null || { echo "error: $tool is required" >&2; exit 1; }
done

step() { printf '\n==> %s\n' "$*"; }
fail() { echo "FAIL: $*" >&2; exit 1; }

# api METHOD PATH [TOKEN] [JSON_BODY] [PROFILE_ID] -> response body; fails on HTTP >= 400
api() {
  local method=$1 path=$2 token=${3:-} body=${4:-} profile=${5:-}
  local args=(-sS -X "$method" -w '\n%{http_code}' -H 'Accept: application/json')
  [[ -n $token ]] && args+=(-H "Authorization: Bearer $token")
  [[ -n $profile ]] && args+=(-H "X-Profile-Id: $profile")
  [[ -n $body ]] && args+=(-H 'Content-Type: application/json' -d "$body")
  local response status
  response=$(curl "${args[@]}" "$API$path")
  status=${response##*$'\n'}
  response=${response%$'\n'*}
  if (( status >= 400 )); then
    fail "$method $path -> HTTP $status: $response"
  fi
  printf '%s' "$response"
}

login() {
  api POST /api/auth/login "" "$(jq -n --arg e "$1" --arg p "$2" '{email: $e, password: $p}')" | jq -r .accessToken
}

step "Checking API health at $API"
curl -sf "$API/actuator/health" >/dev/null || fail "API is not reachable; run 'docker compose up --build' first"

SAMPLE="${1:-}"
if [[ -z $SAMPLE ]]; then
  SAMPLE="${TMPDIR:-/tmp}/marquee-big-buck-bunny-10s.mp4"
  if [[ ! -s $SAMPLE ]]; then
    step "Downloading sample clip"
    curl -fSL --retry 3 -o "$SAMPLE" "$SAMPLE_URL"
  fi
fi
[[ -s $SAMPLE ]] || fail "sample file $SAMPLE not found"
echo "Using $SAMPLE ($(wc -c <"$SAMPLE" | tr -d ' ') bytes)"

step "Logging in as admin $ADMIN_EMAIL"
ADMIN_TOKEN=$(login "$ADMIN_EMAIL" "$ADMIN_PASSWORD")

GENRE_ID=$(api GET /api/admin/genres "$ADMIN_TOKEN" | jq -r '[.[] | select(.name == "Animation")][0].id // empty')

step "Creating title"
TITLE_BODY=$(jq -n --arg genre "$GENRE_ID" '{
  type: "MOVIE",
  name: "Big Buck Bunny",
  synopsis: "A giant rabbit takes revenge on three bullying rodents. (Blender Foundation, CC BY 3.0)",
  releaseYear: 2008,
  maturityRating: "G",
  genreIds: (if $genre == "" then [] else [($genre | tonumber)] end)
}')
TITLE_ID=$(api POST /api/admin/titles "$ADMIN_TOKEN" "$TITLE_BODY" | jq -r .id)
echo "Title $TITLE_ID"

step "Requesting presigned upload"
ASSET=$(api POST /api/admin/assets "$ADMIN_TOKEN" "{\"titleId\": $TITLE_ID}")
ASSET_ID=$(jq -r .assetId <<<"$ASSET")
UPLOAD_URL=$(jq -r .uploadUrl <<<"$ASSET")
CONTENT_TYPE=$(jq -r .contentType <<<"$ASSET")
echo "Asset $ASSET_ID"

step "Uploading source directly to MinIO"
curl -sSf -X PUT -H "Content-Type: $CONTENT_TYPE" --upload-file "$SAMPLE" "$UPLOAD_URL" >/dev/null

step "Completing upload (queues transcode job)"
api POST "/api/admin/assets/$ASSET_ID/complete" "$ADMIN_TOKEN" >/dev/null

step "Waiting for transcode (timeout ${TIMEOUT_SECONDS}s)"
deadline=$((SECONDS + TIMEOUT_SECONDS))
while :; do
  STATUS_JSON=$(api GET "/api/admin/assets/$ASSET_ID" "$ADMIN_TOKEN")
  STATUS=$(jq -r .status <<<"$STATUS_JSON")
  printf '  %s\n' "$STATUS"
  case $STATUS in
    READY) break ;;
    FAILED) fail "transcode failed: $(jq -r .errorMessage <<<"$STATUS_JSON")" ;;
  esac
  (( SECONDS < deadline )) || fail "timed out waiting for READY"
  sleep 3
done
echo "Duration: $(jq -r .durationSeconds <<<"$STATUS_JSON")s"

step "Publishing title"
api POST "/api/admin/titles/$TITLE_ID/publish" "$ADMIN_TOKEN" >/dev/null

step "Requesting playback as viewer $USER_EMAIL"
USER_TOKEN=$(login "$USER_EMAIL" "$USER_PASSWORD")
PROFILE_ID=$(api GET /api/profiles "$USER_TOKEN" | jq -r '[.[] | select(.isKids == false)][0].id')
MANIFEST_PATH=$(api GET "/api/playback/$ASSET_ID" "$USER_TOKEN" "" "$PROFILE_ID" | jq -r .manifestUrl)
MANIFEST_URL="$API$MANIFEST_PATH"

MASTER=$(curl -sSf "$MANIFEST_URL")
VARIANTS=$(grep -c '^#EXT-X-STREAM-INF' <<<"$MASTER" || true)
echo "$MASTER"
(( VARIANTS >= 2 )) || fail "expected at least 2 variants, found $VARIANTS"

NO_TOKEN_STATUS=$(curl -s -o /dev/null -w '%{http_code}' "$API/stream/$ASSET_ID/master.m3u8")
[[ $NO_TOKEN_STATUS == 401 || $NO_TOKEN_STATUS == 403 ]] || fail "stream without token returned $NO_TOKEN_STATUS"

cat <<DONE

OK: asset $ASSET_ID is READY with $VARIANTS variants; stream without token -> $NO_TOKEN_STATUS.

Play it (token valid for ${STREAM_TOKEN_TTL_HOURS:-2}h):
  $MANIFEST_URL

  VLC:    Media > Open Network Stream, paste the URL
  Safari: paste the URL into the address bar
DONE
