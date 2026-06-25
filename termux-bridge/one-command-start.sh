#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

cd "$(dirname "$0")"
./chmod-all.sh || true
./start.sh

if [ -n "${TELEGRAM_BOT_TOKEN:-}" ]; then
  ./start-telegram-background.sh
else
  echo "TELEGRAM_BOT_TOKEN not set, skipping Telegram bot."
fi
