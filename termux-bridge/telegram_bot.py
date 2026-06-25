#!/usr/bin/env python3
"""
Optional Telegram polling bot for the Termux -> PokeClaw bridge.

No webhook, no public server required. The bot polls Telegram from Termux.

Routing model:
- Normal messages and /task go to PokeClaw.
- /chat goes to PokeClaw chat.
- /kali goes to Kali Security Orchestrator after its own token + allowlist policy.
"""

from __future__ import annotations

import os
import time
from typing import Any, Dict, Optional, Tuple

import requests

TOKEN = os.getenv("TELEGRAM_BOT_TOKEN", "").strip()
BRIDGE_URL = os.getenv("POKECLAW_BRIDGE_URL", "http://127.0.0.1:8787").rstrip("/")
BRIDGE_TOKEN = os.getenv("POKECLAW_BRIDGE_TOKEN", "").strip()
KALI_ORCH_URL = os.getenv("KALI_ORCH_URL", "http://127.0.0.1:8899").rstrip("/")
KALI_ORCH_TOKEN = os.getenv("KALI_ORCH_TOKEN", "").strip()
ALLOWED_CHAT_ID = os.getenv("TELEGRAM_ALLOWED_CHAT_ID", "").strip()
POLL_TIMEOUT = int(os.getenv("TELEGRAM_POLL_TIMEOUT", "30"))

if not TOKEN:
    raise SystemExit("Missing TELEGRAM_BOT_TOKEN environment variable")

API = f"https://api.telegram.org/bot{TOKEN}"


def tg(method: str, payload: Dict[str, Any]) -> Dict[str, Any]:
    r = requests.post(f"{API}/{method}", json=payload, timeout=60)
    r.raise_for_status()
    return r.json()


def send(chat_id: int, text: str) -> None:
    tg("sendMessage", {"chat_id": chat_id, "text": text[:3900]})


def bridge(endpoint: str, payload: Dict[str, Any]) -> Dict[str, Any]:
    headers = {"Content-Type": "application/json"}
    if BRIDGE_TOKEN:
        headers["X-Bridge-Token"] = BRIDGE_TOKEN
    r = requests.post(f"{BRIDGE_URL}/{endpoint}", json=payload, headers=headers, timeout=30)
    try:
        body = r.json()
    except Exception:
        body = {"ok": False, "error": r.text}
    body["http_status"] = r.status_code
    return body


def kali_run(action: str, target: str, top_ports: int = 50) -> Dict[str, Any]:
    if not KALI_ORCH_TOKEN:
        return {"ok": False, "error": "Missing KALI_ORCH_TOKEN in Termux environment"}

    payload: Dict[str, Any] = {
        "action": action,
        "target": target,
        "args": {"top_ports": top_ports},
    }
    r = requests.post(
        f"{KALI_ORCH_URL}/run",
        json=payload,
        headers={"Content-Type": "application/json", "X-Orchestrator-Token": KALI_ORCH_TOKEN},
        timeout=140,
    )
    try:
        body = r.json()
    except Exception:
        body = {"ok": False, "error": r.text}
    body["http_status"] = r.status_code
    return body


def allowed(chat_id: int) -> bool:
    return not ALLOWED_CHAT_ID or str(chat_id) == ALLOWED_CHAT_ID


def parse_command(text: str) -> Tuple[str, str]:
    stripped = text.strip()
    if stripped.startswith("/task "):
        return "task", stripped[len("/task "):].strip()
    if stripped.startswith("/chat "):
        return "chat", stripped[len("/chat "):].strip()
    if stripped.startswith("/run "):
        return "task", stripped[len("/run "):].strip()
    if stripped.startswith("/kali "):
        return "kali", stripped[len("/kali "):].strip()
    if stripped.startswith("/help") or stripped.startswith("/start"):
        return "help", ""
    # Default: treat normal messages as PokeClaw tasks.
    return "task", stripped


def format_kali_result(data: Dict[str, Any]) -> str:
    if not data.get("ok"):
        return f"Kali failed: {data.get('error') or data}"

    action = data.get("action")
    target = data.get("target")
    report = data.get("report") or {}
    result = data.get("result") or {}

    lines = [
        "Kali result",
        f"Action: {action}",
        f"Target: {target}",
        f"Report: {report.get('report_id', 'n/a')}",
        "",
    ]

    if "inventory" in result:
        lines.append("Inventory completed. Check report for DNS, ping and service scan.")
    elif "stdout" in result:
        lines.append(str(result.get("stdout") or "No output")[:2500])
    elif "headers" in result:
        lines.append(f"HTTP {result.get('status_code')} server={result.get('server')} content_type={result.get('content_type')}")
    elif "resolved_ips" in result:
        lines.append("Resolved: " + ", ".join(result.get("resolved_ips") or []))
    else:
        lines.append(str(result)[:2500])

    return "\n".join(lines)


def handle_kali(chat_id: int, body: str) -> None:
    parts = body.split()
    if len(parts) < 2:
        send(
            chat_id,
            "Usage:\n"
            "/kali ping 192.168.1.20\n"
            "/kali dns scanme.nmap.org\n"
            "/kali web_headers http://192.168.1.20\n"
            "/kali scan_host 192.168.1.20\n"
            "/kali inventory 192.168.1.20"
        )
        return

    action = parts[0].strip().lower()
    target = parts[1].strip()
    top_ports = 50
    if len(parts) >= 3:
        try:
            top_ports = max(1, min(int(parts[2]), 200))
        except ValueError:
            top_ports = 50

    send(chat_id, f"Sending to Kali Orchestrator: {action} {target}")
    data = kali_run(action, target, top_ports=top_ports)
    send(chat_id, format_kali_result(data))


def handle_message(msg: Dict[str, Any]) -> None:
    chat = msg.get("chat") or {}
    chat_id = chat.get("id")
    text = (msg.get("text") or "").strip()
    if not chat_id or not text:
        return

    if not allowed(chat_id):
        send(chat_id, "Not allowed.")
        return

    mode, body = parse_command(text)
    if mode == "help":
        send(
            chat_id,
            "PokeClaw Termux Bridge\n\n"
            "PokeClaw commands:\n"
            "/task how much battery left\n"
            "/chat what can you do?\n\n"
            "Kali defensive commands:\n"
            "/kali ping 192.168.1.20\n"
            "/kali dns scanme.nmap.org\n"
            "/kali web_headers http://192.168.1.20\n"
            "/kali scan_host 192.168.1.20\n"
            "/kali inventory 192.168.1.20\n\n"
            "Normal messages are treated as /task. Kali actions are policy-checked by the orchestrator."
        )
        return

    if not body:
        send(chat_id, "Empty command.")
        return

    if mode == "kali":
        handle_kali(chat_id, body)
        return

    send(chat_id, f"Sending to PokeClaw as {mode}...")
    result = bridge(mode, {mode: body})
    if result.get("ok"):
        send(chat_id, f"PokeClaw launched. request_id={result.get('id')}")
    else:
        send(chat_id, f"Failed: {result.get('error') or result.get('stderr') or result}")


def main() -> None:
    print("Telegram bot started. Normal messages become PokeClaw tasks. /kali routes to Kali Orchestrator.")
    offset: Optional[int] = None
    while True:
        try:
            payload: Dict[str, Any] = {"timeout": POLL_TIMEOUT}
            if offset is not None:
                payload["offset"] = offset
            data = tg("getUpdates", payload)
            for update in data.get("result", []):
                offset = update["update_id"] + 1
                if "message" in update:
                    handle_message(update["message"])
        except KeyboardInterrupt:
            raise
        except Exception as exc:
            print(f"poll error: {exc}")
            time.sleep(5)


if __name__ == "__main__":
    main()
