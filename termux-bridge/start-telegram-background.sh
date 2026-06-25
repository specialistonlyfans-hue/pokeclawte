#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

cd "$(dirname "$0")"

if [ -z "${TELEGRAM_BOT_TOKEN:-}" ]; then
  echo "Missing TELEGRAM_BOT_TOKEN"
  exit 1
fi

LOG_FILE="telegram.log"
PID_FILE="telegram.pid"

if [ -f "$PID_FILE" ]; then
  OLD_PID="$(cat "$PID_FILE" || true)"
  if [ -n "${OLD_PID:-}" ] && kill -0 "$OLD_PID" 2>/dev/null; then
    echo "Telegram bot already running with PID $OLD_PID"
    exit 0
  fi
fi

nohup python telegram_bot.py >> "$LOG_FILE" 2>&1 &
echo $! > "$PID_FILE"

echo "Telegram bot started with PID $(cat "$PID_FILE")"
echo "Log: $PWD/$LOG_FILE"
