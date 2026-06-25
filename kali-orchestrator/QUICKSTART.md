# Quickstart

## On Kali

```bash
cd kali-orchestrator
chmod +x *.sh *.py
./install.sh
nano config.json
```

Set a real token:

```json
"api_token": "CHANGE_THIS_TO_A_LONG_RANDOM_SECRET"
```

Set your own lab ranges:

```json
"allowed_targets": [
  "127.0.0.1/32",
  "192.168.1.0/24"
]
```

Start:

```bash
export KALI_ORCH_TOKEN="CHANGE_THIS_TO_A_LONG_RANDOM_SECRET"
./start.sh
./test.sh 127.0.0.1 inventory
```

## From Termux

```bash
cd $HOME/pokeclawte/termux-bridge
export KALI_ORCH_URL="http://KALI_IP:8899"
export KALI_ORCH_TOKEN="CHANGE_THIS_TO_A_LONG_RANDOM_SECRET"
python kali_client.py inventory 192.168.1.20 --top-ports 50
```

## Actions

```text
ping
dns
web_headers
scan_host
inventory
```
