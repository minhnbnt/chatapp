#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

if command -v podman &>/dev/null; then
  COMPOSE="podman compose"
else
  COMPOSE="docker compose"
fi

cd "$PROJECT_DIR"

echo "=== Stopping all containers ==="
$COMPOSE down -v
echo "=== Done ==="
