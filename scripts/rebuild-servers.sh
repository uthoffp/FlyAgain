#!/usr/bin/env bash
# Rebuild and restart all FlyAgain server services.
# Usage: ./scripts/rebuild-servers.sh [service...]
#   No args  → rebuild all four game services (keeps postgres & redis running)
#   With args → rebuild only the named services, e.g. ./scripts/rebuild-servers.sh world-service

set -euo pipefail
cd "$(dirname "$0")/.."

ALL_SERVICES=(database-service login-service account-service world-service)
SERVICES=("${@:-${ALL_SERVICES[@]}}")

echo "==> Building Gradle projects..."
(cd server && ./gradlew build -x test --parallel -q)

echo "==> Rebuilding Docker images for: ${SERVICES[*]}"
docker compose build "${SERVICES[@]}"

echo "==> Restarting services: ${SERVICES[*]}"
docker compose up -d "${SERVICES[@]}"

echo "==> Tailing logs (Ctrl+C to stop)..."
docker compose logs -f "${SERVICES[@]}"
