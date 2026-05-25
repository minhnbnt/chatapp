#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BASE_URL="${BASE_URL:-http://localhost:8080}"
INFLUXDB_HOST="${INFLUXDB_HOST:-localhost}"
INFLUXDB_PORT="${INFLUXDB_PORT:-8086}"
INFLUXDB_USER="${INFLUXDB_USER:-k6}"
INFLUXDB_PASS="${INFLUXDB_PASS:-k6password1}"
INFLUXDB_DB="${INFLUXDB_DB:-k6}"

# Detect container runtime
if command -v podman &>/dev/null; then
  RUNTIME="podman"
elif command -v docker &>/dev/null; then
  RUNTIME="docker"
else
  echo "Neither podman nor docker found"
  exit 1
fi

echo "=== Running with: $RUNTIME ==="
echo "BASE_URL=$BASE_URL"
echo "InfluxDB=http://$INFLUXDB_HOST:$INFLUXDB_PORT/$INFLUXDB_DB"

run_k6() {
  local script=$1
  local tag=$2

  echo ""
  echo "=== Running $tag ($script) ==="
  echo ""

  $RUNTIME run --rm \
    -e BASE_URL="$BASE_URL" \
    -v "$SCRIPT_DIR:/scripts:Z" \
    --network host \
    grafana/k6 run \
    --tag testid="$tag" \
    --out "influxdb=http://$INFLUXDB_USER:$INFLUXDB_PASS@$INFLUXDB_HOST:$INFLUXDB_PORT/$INFLUXDB_DB" \
    "/scripts/$script"
}

run_k6 "latency-http.js"      "latency-http-001"
run_k6 "stress-chat.js"       "stress-chat-001"
# run_k6 "latency-websocket.js" "latency-ws-001"

echo ""
echo "=== Done ==="