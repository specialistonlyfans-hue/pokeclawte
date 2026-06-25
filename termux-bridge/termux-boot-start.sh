#!/data/data/com.termux/files/usr/bin/bash
# Optional helper for Termux:Boot.
# Copy this file to ~/.termux/boot/pokeclaw-bridge and make it executable.

sleep 10
cd "$HOME/pokeclawte/termux-bridge" || exit 0
./start.sh
