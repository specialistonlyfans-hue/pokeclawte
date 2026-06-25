#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"
PID_FILE="orchestrator.pid"

if [ -f "$PID_FILE" ]; then
  PID="$(cat "$PID_FILE" || true)"
  if [ -n "${PID:-}" ] && kill -0 "$PID" 2>/dev/null; then
    echo "orchestrator: running PID $PID"
  else
    echo "orchestrator: pid file exists but process not running"
  fi
else
  echo "orchestrator: not running"
fi

echo "health endpoint: http://127.0.0.1:8899/health"
