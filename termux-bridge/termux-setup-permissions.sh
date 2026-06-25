#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

if command -v termux-wake-lock >/dev/null 2>&1; then
  termux-wake-lock || true
  echo "termux-wake-lock requested."
else
  echo "termux-wake-lock not found. Install Termux:API app + pkg install termux-api."
fi

echo "Now set manually in Android:"
echo "  Battery -> Termux -> Unrestricted"
echo "  Battery -> PokeClaw -> Unrestricted"
echo "  Autostart -> allow Termux, if your ROM has this"
