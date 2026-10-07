#!/usr/bin/env bash
# Runs a dedicated instance of the existing recommendation system for Marquee (spec Phase 3).
#
#   scripts/recsys.sh up | down | stop | start | status | logs [service]
#
# RECSYS_DIR points at the recsys checkout (default: ~/Desktop/Recommendation engine). Only the
# services Marquee needs are started; LLM enrichment and the monitoring stack are left out to fit
# next to Marquee in Docker's memory. Images are built by the recsys project (`docker compose build`
# there); pass --build to `up` to (re)build them from the checkout.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RECSYS_DIR="${RECSYS_DIR:-$HOME/Desktop/Recommendation engine}"
SERVICES=(kafka kafka-init schema-registry redis qdrant postgres
          ingestion-api catalog-service stream-processor embedding-worker recommendation-api)

[[ -f "$RECSYS_DIR/docker-compose.yml" ]] || { echo "error: recsys checkout not found at $RECSYS_DIR (set RECSYS_DIR)" >&2; exit 1; }

compose() {
  docker compose -p marquee-recsys \
    --project-directory "$RECSYS_DIR" \
    --env-file "$ROOT/recsys/recsys.env" \
    -f "$RECSYS_DIR/docker-compose.yml" \
    -f "$ROOT/recsys/compose.marquee.yml" \
    "$@"
}

case "${1:-}" in
  up)
    shift
    build="--no-build"
    [[ "${1:-}" == "--build" ]] && build="--build"
    compose up -d $build "${SERVICES[@]}"
    echo "Waiting for the recommendation API on :18080 ..."
    for _ in $(seq 1 90); do
      curl -sf -o /dev/null localhost:18080/actuator/health && { echo "recsys is up (ingest :18081, catalog :18082, recommendations :18080)"; exit 0; }
      sleep 2
    done
    echo "error: recommendation API did not become healthy; see: scripts/recsys.sh logs" >&2
    exit 1
    ;;
  down) compose down ;;               # keeps volumes; add -v manually to wipe the instance
  stop) compose stop ;;
  start) compose start ;;
  status) compose ps ;;
  logs) shift; compose logs --tail=200 "$@" ;;
  *) echo "usage: $0 up [--build] | down | stop | start | status | logs [service]" >&2; exit 2 ;;
esac
