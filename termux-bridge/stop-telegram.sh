#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

cd "$(dirname "$0")"
PID_FILE="telegram.pid"

if [ ! -f "$PID_FILE" ]; then
  echo "No telegram.pid found. Bot may not be running."
  exit 0
fi

PID="$(cat "$PID_FILE" || true)"
if [ -n "${PID:-}" ] && kill -0 "$PID" 2>/dev/null; then
  kill "$PID"
  echo "Stopped Telegram bot PID $PID"
else
  echo "Telegram PID not running"
fi

rm -f "$PID_FILE"
