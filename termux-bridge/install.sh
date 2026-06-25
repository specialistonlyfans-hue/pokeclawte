#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

cd "$(dirname "$0")"

echo "[1/5] Updating Termux packages"
pkg update -y

echo "[2/5] Installing Python and helper packages"
pkg install -y python termux-api curl jq

echo "[3/5] Installing Python dependencies"
python -m pip install --upgrade pip
python -m pip install flask requests

echo "[4/5] Creating local config if missing"
if [ ! -f config.json ]; then
  cp config.example.json config.json
  echo "Created config.json"
else
  echo "config.json already exists, keeping it"
fi

echo "[5/5] Making scripts executable"
chmod +x bridge.py telegram_bot.py start.sh stop.sh test.sh 2>/dev/null || true

echo ""
echo "Done. Next:"
echo "  1. Enable PokeClaw: Settings -> Remote Control -> External Automation"
echo "  2. Run: ./start.sh"
echo "  3. Test: ./test.sh"
echo ""
echo "Optional but recommended:"
echo "  termux-wake-lock"
echo "  Android Settings -> Battery -> Termux + PokeClaw -> Unrestricted"
