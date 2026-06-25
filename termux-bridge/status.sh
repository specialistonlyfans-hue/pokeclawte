#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

cd "$(dirname "$0")"

for name in bridge telegram; do
  PID_FILE="$name.pid"
  if [ -f "$PID_FILE" ]; then
    PID="$(cat "$PID_FILE" || true)"
    if [ -n "${PID:-}" ] && kill -0 "$PID" 2>/dev/null; then
      echo "$name: running PID $PID"
    else
      echo "$name: pid file exists but process not running"
    fi
  else
    echo "$name: not running"
  fi
done

if command -v curl >/dev/null 2>&1; then
  echo ""
  echo "bridge health:"
  curl -s http://127.0.0.1:8787/health || true
  echo ""
fi
