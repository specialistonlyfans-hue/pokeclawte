#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

BASE_URL="${POKECLAW_BRIDGE_URL:-http://127.0.0.1:8787}"
TOKEN_HEADER=()

if [ -n "${POKECLAW_BRIDGE_TOKEN:-}" ]; then
  TOKEN_HEADER=(-H "X-Bridge-Token: ${POKECLAW_BRIDGE_TOKEN}")
fi

echo "Health check:"
curl -s "$BASE_URL/health" | jq . || true

echo ""
echo "Sending test task to PokeClaw:"
curl -s -X POST "$BASE_URL/task" \
  -H "Content-Type: application/json" \
  "${TOKEN_HEADER[@]}" \
  -d '{"task":"how much battery left"}' | jq . || true

echo ""
echo "If PokeClaw opened and started a task, the bridge works."
