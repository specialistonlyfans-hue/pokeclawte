# Termux integration

The Kali orchestrator is separate from PokeClaw.

Recommended flow:

```text
Telegram / Termux command
        ↓
termux-bridge/kali_client.py
        ↓
Kali orchestrator /run
        ↓
report JSON + Markdown
```

First test from Termux:

```bash
export KALI_ORCH_URL="http://KALI_IP:8899"
export KALI_ORCH_TOKEN="YOUR_TOKEN"
python kali_client.py inventory 192.168.1.20 --top-ports 50
```

Later Telegram command idea:

```text
/kali inventory 192.168.1.20
/kali scan_host 192.168.1.20
/kali web_headers http://192.168.1.20
```

Do not connect this to broad internet targets. Keep `allowed_targets` tight.
