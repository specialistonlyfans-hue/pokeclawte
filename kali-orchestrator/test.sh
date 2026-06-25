#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"
TARGET="${1:-127.0.0.1}"
ACTION="${2:-inventory}"
TOKEN="${KALI_ORCH_TOKEN:-CHANGE_ME}"
URL="${KALI_ORCH_URL:-http://127.0.0.1:8899}"

curl -s -X POST "$URL/run" \
  -H "Content-Type: application/json" \
  -H "X-Orchestrator-Token: $TOKEN" \
  -d "{\"action\":\"$ACTION\",\"target\":\"$TARGET\",\"args\":{\"top_ports\":50}}" | jq .
