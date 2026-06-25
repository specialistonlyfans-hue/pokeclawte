#!/usr/bin/env python3
"""
Optional Telegram polling bot for the Termux -> PokeClaw bridge.

No webhook, no public server required. The bot polls Telegram from Termux and
forwards commands to the local bridge.
"""

from __future__ import annotations

import os
import time
from typing import Any, Dict, Optional

import requests

TOKEN = os.getenv("TELEGRAM_BOT_TOKEN", "").strip()
BRIDGE_URL = os.getenv("POKECLAW_BRIDGE_URL", "http://127.0.0.1:8787").rstrip("/")
BRIDGE_TOKEN = os.getenv("POKECLAW_BRIDGE_TOKEN", "").strip()
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


def allowed(chat_id: int) -> bool:
    return not ALLOWED_CHAT_ID or str(chat_id) == ALLOWED_CHAT_ID


def parse_command(text: str) -> tuple[str, str]:
    stripped = text.strip()
    if stripped.startswith("/task "):
        return "task", stripped[len("/task "):].strip()
    if stripped.startswith("/chat "):
        return "chat", stripped[len("/chat "):].strip()
    if stripped.startswith("/run "):
        return "task", stripped[len("/run "):].strip()
    if stripped.startswith("/help") or stripped.startswith("/start"):
        return "help", ""
    # Default: treat normal messages as PokeClaw tasks.
    return "task", stripped


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
            "Commands:\n"
            "/task how much battery left\n"
            "/chat what can you do?\n\n"
            "Normal messages are treated as /task."
        )
        return

    if not body:
        send(chat_id, "Empty command.")
        return

    send(chat_id, f"Sending to PokeClaw as {mode}...")
    result = bridge(mode, {mode: body})
    if result.get("ok"):
        send(chat_id, f"PokeClaw launched. request_id={result.get('id')}")
    else:
        send(chat_id, f"Failed: {result.get('error') or result.get('stderr') or result}")


def main() -> None:
    print("Telegram bot started. Normal messages become PokeClaw tasks.")
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
