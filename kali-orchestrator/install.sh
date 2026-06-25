#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

echo "[1/5] Installing OS packages"
if command -v apt >/dev/null 2>&1; then
  sudo apt update
  sudo apt install -y python3 python3-venv python3-pip nmap curl jq openssh-client
else
  echo "apt not found. Install python3, pip, nmap, curl, jq manually."
fi

echo "[2/5] Creating venv"
python3 -m venv .venv

# shellcheck disable=SC1091
source .venv/bin/activate

echo "[3/5] Installing Python deps"
pip install --upgrade pip
pip install -r requirements.txt

echo "[4/5] Creating config.json if missing"
if [ ! -f config.json ]; then
  cp config.example.json config.json
  echo "Created config.json. Edit api_token and allowed_targets before running."
else
  echo "config.json exists, keeping it."
fi

echo "[5/5] Making scripts executable"
chmod +x orchestrator.py start.sh stop.sh test.sh status.sh 2>/dev/null || true

echo ""
echo "Next:"
echo "  nano config.json"
echo "  ./start.sh"
echo "  ./test.sh 127.0.0.1"
