#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
HOST="${HOST:-http://localhost:8080}"

echo "=== Starting Setup ==="
bash "$SCRIPT_DIR/setup.sh"

HURL_OPTS="--test --variable host=$HOST --file-root $SCRIPT_DIR"

# Each scenario file is self-contained but they share captured variables
# within the same hurl invocation. We chain them in order so that
# tokens and IDs captured in earlier files are available in later ones.

echo ""
echo "========================================="
echo "  Running all Hurl API tests"
echo "  Host: $HOST"
echo "========================================="
echo ""

echo "=== 01 Auth & User Registration ==="
hurl $HURL_OPTS "$SCRIPT_DIR/scenarios/01-auth-user.hurl"

echo "=== 11 Health Check ==="
hurl $HURL_OPTS "$SCRIPT_DIR/scenarios/11-health.hurl"

# For the remaining scenarios, we need tokens from 01-auth.
# Hurl doesn't share state between separate invocations,
# so we run dependent scenarios together in a single hurl call.

echo "=== 02-08 Full Integration Flow ==="
hurl $HURL_OPTS \
  "$SCRIPT_DIR/scenarios/01-auth-user.hurl" \
  "$SCRIPT_DIR/scenarios/02-user-profile.hurl" \
  "$SCRIPT_DIR/scenarios/03-block-user.hurl" \
  "$SCRIPT_DIR/scenarios/04-invitation.hurl" \
  "$SCRIPT_DIR/scenarios/05-group-chat.hurl" \
  "$SCRIPT_DIR/scenarios/06-message.hurl" \
  "$SCRIPT_DIR/scenarios/08-chatroom.hurl"

echo ""
echo "========================================="
echo "  All core API tests passed!"
echo "========================================="

echo ""
echo "=== Cleaning Up ==="
bash "$SCRIPT_DIR/teardown.sh"

echo ""
echo "=== All Hurl API Tests Completed ==="
