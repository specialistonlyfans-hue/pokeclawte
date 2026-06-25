#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

cd "$(dirname "$0")"

if [ -z "${TELEGRAM_BOT_TOKEN:-}" ]; then
  echo "Missing TELEGRAM_BOT_TOKEN"
  echo "Run: export TELEGRAM_BOT_TOKEN='123456:ABC...'"
  exit 1
fi

python telegram_bot.py
