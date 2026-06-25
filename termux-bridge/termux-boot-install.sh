#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

BOOT_DIR="$HOME/.termux/boot"
mkdir -p "$BOOT_DIR"
cp "$(dirname "$0")/termux-boot-start.sh" "$BOOT_DIR/pokeclaw-bridge"
chmod +x "$BOOT_DIR/pokeclaw-bridge"

echo "Installed Termux:Boot script: $BOOT_DIR/pokeclaw-bridge"
echo "Install the Termux:Boot app from F-Droid, then open it once."
