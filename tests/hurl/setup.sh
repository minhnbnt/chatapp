#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Detect container runtime
if command -v podman &>/dev/null; then
  COMPOSE="podman compose"
else
  COMPOSE="docker compose"
fi

cd "$PROJECT_DIR"

echo "=== Building app ==="
$COMPOSE build app

echo "=== Starting infrastructure ==="
$COMPOSE up -d postgres redis artemis
echo "Waiting for services to be ready..."
sleep 15

echo "=== Starting app ==="
$COMPOSE up -d app
echo "Waiting for app to start..."
sleep 20

echo "=== Infrastructure Ready ==="
