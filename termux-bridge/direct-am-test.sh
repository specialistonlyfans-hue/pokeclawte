#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

TASK="${1:-how much battery left}"

am start \
  -n io.agents.pokeclaw/.automation.ExternalAutomationActivity \
  -a io.agents.pokeclaw.RUN_TASK \
  --es task "$TASK"

echo "Sent direct intent to PokeClaw: $TASK"
