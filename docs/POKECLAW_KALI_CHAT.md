# PokeClaw Kali Chat Integration

This makes PokeClaw itself the chat UI for the Kali Security Orchestrator.

Telegram is optional. You can use PokeClaw directly.

## Flow

```text
PokeClaw Chat
  ↓
/kali command parser
  ↓
KaliOrchestratorClient.kt
  ↓
Kali Orchestrator API
  ↓
JSON + Markdown report
  ↓
PokeClaw Chat answer
```

## First setup in PokeClaw chat

Start your Kali Orchestrator first, then type in PokeClaw chat:

```text
/kali config http://KALI_IP:8899 YOUR_LONG_RANDOM_TOKEN
```

Example:

```text
/kali config http://192.168.1.50:8899 change-this-token
```

Check connection:

```text
/kali status
```

## Commands

```text
/kali ping 192.168.1.20
/kali dns_check scanme.nmap.org
/kali web_check http://192.168.1.20
/kali tls_check example.com
/kali scan_host 192.168.1.20 50
/kali service_inventory 192.168.1.20 50
```

Aliases:

```text
/kali dns scanme.nmap.org
/kali web http://192.168.1.20
/kali web_headers http://192.168.1.20
/kali tls example.com
/kali inventory 192.168.1.20 50
```

## Clear config

```text
/kali clear
```

## Safety boundary

The PokeClaw chat client only calls the Kali Orchestrator's policy-checked actions.

It does not implement:

```text
free shell
exploit autopilot
credential attacks
phishing flows
Wi-Fi disruption
malware or payload generation
stealth behavior
```

The Kali Orchestrator still enforces:

```text
api token
target allowlist
action allowlist
reports/logging
```

## Android network note

The MVP supports local/LAN HTTP like:

```text
http://127.0.0.1:8899
http://192.168.1.50:8899
```

The app network config allows cleartext traffic for this lab API. Keep the orchestrator token strong and keep the API on your private network.
