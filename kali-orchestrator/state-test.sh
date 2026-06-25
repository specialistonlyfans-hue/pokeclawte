#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

TOKEN="${KALI_ORCH_TOKEN:-CHANGE_ME}"
URL="${KALI_ORCH_URL:-http://127.0.0.1:8899}"

echo "Findings:"
curl -s "$URL/findings?limit=20" -H "X-Orchestrator-Token: $TOKEN" | jq .

echo "Evidence:"
curl -s "$URL/evidence?limit=20" -H "X-Orchestrator-Token: $TOKEN" | jq .

echo "Jobs:"
curl -s "$URL/jobs?limit=20" -H "X-Orchestrator-Token: $TOKEN" | jq .
