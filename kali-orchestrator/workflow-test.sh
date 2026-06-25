#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

TARGET="${1:-127.0.0.1}"
WORKFLOW="${2:-quick_host}"
TOKEN="${KALI_ORCH_TOKEN:-CHANGE_ME}"
URL="${KALI_ORCH_URL:-http://127.0.0.1:8899}"
TOP_PORTS="${3:-50}"

curl -s -X POST "$URL/workflow" \
  -H "Content-Type: application/json" \
  -H "X-Orchestrator-Token: $TOKEN" \
  -d "{\"workflow\":\"$WORKFLOW\",\"target\":\"$TARGET\",\"args\":{\"top_ports\":$TOP_PORTS}}" | jq .
