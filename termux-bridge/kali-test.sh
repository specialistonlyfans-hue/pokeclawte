#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

cd "$(dirname "$0")"

ACTION="${1:-inventory}"
TARGET="${2:-127.0.0.1}"

python kali_client.py "$ACTION" "$TARGET" --top-ports 50
