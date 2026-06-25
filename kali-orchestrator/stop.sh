#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"
PID_FILE="orchestrator.pid"

if [ ! -f "$PID_FILE" ]; then
  echo "No orchestrator.pid found."
  exit 0
fi

PID="$(cat "$PID_FILE" || true)"
if [ -n "${PID:-}" ] && kill -0 "$PID" 2>/dev/null; then
  kill "$PID"
  echo "Stopped Kali orchestrator PID $PID"
else
  echo "PID not running"
fi

rm -f "$PID_FILE"
