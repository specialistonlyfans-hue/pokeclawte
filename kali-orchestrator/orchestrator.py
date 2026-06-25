#!/usr/bin/env python3
"""
Kali Security Orchestrator.

Purpose: authorized security lab and own-systems inventory.
Design: no arbitrary shell, target allowlist, action allowlist, JSON/Markdown reports.

This intentionally does NOT implement autonomous exploitation, credential attacks,
phishing, deauth/Wi-Fi disruption, malware, payload generation, or stealth.
"""

from __future__ import annotations

import ipaddress
import json
import os
import re
import shutil
import socket
import ssl
import subprocess
import time
import uuid
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple
from urllib.parse import urlparse

import requests
from flask import Flask, jsonify, request, send_file

ROOT = Path(__file__).resolve().parent
CONFIG_PATH = Path(os.getenv("KALI_ORCH_CONFIG", ROOT / "config.json"))
REPORT_DIR = ROOT / "reports"
REPORT_DIR.mkdir(exist_ok=True)

DEFAULT_CONFIG: Dict[str, Any] = {
    "host": "127.0.0.1",
    "port": 8899,
    "api_token": "",
    "runner_mode": "local",
    "ssh": {"host": "", "user": "kali", "port": 22, "key_path": "~/.ssh/id_ed25519"},
    "policy": {
        "allowed_targets": ["127.0.0.1/32"],
        "max_top_ports": 200,
        "default_top_ports": 100,
        "timeout_seconds": 90,
        "require_token": True,
        "lab_mode": False,
        "blocked_actions": [
            "exploit",
            "bruteforce",
            "phishing",
            "deauth",
            "malware",
            "payload",
            "credential_attack",
        ],
        "blocked_keywords": [
            "hydra",
            "john",
            "hashcat",
            "msfconsole",
            "msfvenom",
            "airmon-ng",
            "aireplay-ng",
            "ettercap",
            "bettercap",
            "evilginx",
            "gophish",
            "setoolkit",
            "beef-xss",
            "sqlmap",
        ],
    },
}

APP = Flask(__name__)


def deep_update(base: Dict[str, Any], updates: Dict[str, Any]) -> None:
    for key, value in updates.items():
        if isinstance(value, dict) and isinstance(base.get(key), dict):
            deep_update(base[key], value)
        else:
            base[key] = value


def load_config() -> Dict[str, Any]:
    cfg = json.loads(json.dumps(DEFAULT_CONFIG))
    if CONFIG_PATH.exists():
        user_cfg = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
        deep_update(cfg, user_cfg)
    cfg["api_token"] = os.getenv("KALI_ORCH_TOKEN", str(cfg.get("api_token", "")))
    cfg["host"] = os.getenv("KALI_ORCH_HOST", str(cfg.get("host", "127.0.0.1")))
    cfg["port"] = int(os.getenv("KALI_ORCH_PORT", str(cfg.get("port", 8899))))
    return cfg


CONFIG = load_config()
POLICY = CONFIG["policy"]


def now_iso() -> str:
    return time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())


def require_auth() -> Optional[Any]:
    if not POLICY.get("require_token", True):
        return None
    expected = str(CONFIG.get("api_token", "")).strip()
    if not expected or expected == "CHANGE_ME":
        return jsonify({"ok": False, "error": "server api_token not configured"}), 500
    got = request.headers.get("X-Orchestrator-Token", "").strip()
    if got != expected:
        return jsonify({"ok": False, "error": "unauthorized"}), 401
    return None


def safe_target_string(target: str) -> str:
    target = str(target or "").strip()
    if not target or len(target) > 253:
        raise ValueError("invalid target")
    if re.search(r"[^a-zA-Z0-9.\-_:/$?=&%]", target):
        raise ValueError("target contains unsupported characters")
    return target


def target_host(target: str) -> str:
    parsed = urlparse(target if "://" in target else f"//{target}")
    host = parsed.hostname or target
    return host.strip("[]")


def resolve_host(host: str) -> List[str]:
    try:
        return sorted({item[4][0] for item in socket.getaddrinfo(host, None)})
    except Exception:
        return []


def target_allowed(target: str) -> Tuple[bool, str]:
    host = target_host(target)
    allowed = POLICY.get("allowed_targets", [])

    for item in allowed:
        item = str(item).strip()
        if not item:
            continue
        if "/" not in item and item.lower() == host.lower():
            return True, f"host matches allowlist entry {item}"

    ips: List[str] = []
    try:
        ipaddress.ip_address(host)
        ips = [host]
    except ValueError:
        ips = resolve_host(host)

    for ip in ips:
        ip_obj = ipaddress.ip_address(ip)
        for item in allowed:
            try:
                if ip_obj in ipaddress.ip_network(item, strict=False):
                    return True, f"{ip} is inside allowlist {item}"
            except ValueError:
                continue

    return False, f"target {target} is not in allowed_targets"


def check_action_allowed(action: str, target: str) -> None:
    action = action.lower().strip()
    if action in set(POLICY.get("blocked_actions", [])):
        raise PermissionError(f"blocked action: {action}")
    allowed, reason = target_allowed(target)
    if not allowed:
        raise PermissionError(reason)


def check_command_safe(cmd: List[str]) -> None:
    joined = " ".join(cmd).lower()
    for keyword in POLICY.get("blocked_keywords", []):
        if keyword.lower() in joined:
            raise PermissionError(f"blocked command keyword: {keyword}")


def shell_quote(s: str) -> str:
    return "'" + s.replace("'", "'\"'\"'") + "'"


def run_cmd(cmd: List[str], timeout: Optional[int] = None) -> Dict[str, Any]:
    check_command_safe(cmd)
    timeout = timeout or int(POLICY.get("timeout_seconds", 90))
    runner_mode = str(CONFIG.get("runner_mode", "local"))

    if runner_mode == "ssh":
        ssh_cfg = CONFIG.get("ssh", {})
        host = ssh_cfg.get("host")
        user = ssh_cfg.get("user", "kali")
        port = str(ssh_cfg.get("port", 22))
        key_path = os.path.expanduser(str(ssh_cfg.get("key_path", "~/.ssh/id_ed25519")))
        if not host:
            raise RuntimeError("runner_mode=ssh but ssh.host is empty")
        remote = f"{user}@{host}"
        remote_cmd = " ".join(shell_quote(part) for part in cmd)
        full_cmd = ["ssh", "-i", key_path, "-p", port, "-o", "BatchMode=yes", remote, remote_cmd]
    else:
        full_cmd = cmd

    start = time.time()
    proc = subprocess.run(full_cmd, capture_output=True, text=True, timeout=timeout)
    return {
        "cmd": cmd,
        "runner_mode": runner_mode,
        "returncode": proc.returncode,
        "stdout": proc.stdout[-12000:],
        "stderr": proc.stderr[-6000:],
        "duration_seconds": round(time.time() - start, 3),
    }


def action_ping(target: str, args: Dict[str, Any]) -> Dict[str, Any]:
    host = target_host(safe_target_string(target))
    check_action_allowed("ping", host)
    ping_bin = shutil.which("ping") or "ping"
    return run_cmd([ping_bin, "-c", "2", "-W", "2", host], timeout=10)


def action_dns(target: str, args: Dict[str, Any]) -> Dict[str, Any]:
    host = target_host(safe_target_string(target))
    check_action_allowed("dns_check", host)
    ips = resolve_host(host)
    return {"host": host, "resolved_ips": ips, "returncode": 0 if ips else 1, "stdout": "\n".join(ips), "stderr": ""}


def action_web_check(target: str, args: Dict[str, Any]) -> Dict[str, Any]:
    target = safe_target_string(target)
    check_action_allowed("web_check", target)
    url = target if "://" in target else f"http://{target}"
    start = time.time()
    try:
        r = requests.get(url, timeout=10, allow_redirects=True)
        return {
            "url": url,
            "final_url": r.url,
            "status_code": r.status_code,
            "headers": dict(r.headers),
            "server": r.headers.get("Server"),
            "content_type": r.headers.get("Content-Type"),
            "body_sample": r.text[:500],
            "returncode": 0,
            "duration_seconds": round(time.time() - start, 3),
        }
    except Exception as exc:
        return {"url": url, "returncode": 1, "error": str(exc), "duration_seconds": round(time.time() - start, 3)}


def action_tls_check(target: str, args: Dict[str, Any]) -> Dict[str, Any]:
    host = target_host(safe_target_string(target))
    check_action_allowed("tls_check", host)
    port = int(args.get("port") or 443)
    start = time.time()
    try:
        ctx = ssl.create_default_context()
        with socket.create_connection((host, port), timeout=10) as sock:
            with ctx.wrap_socket(sock, server_hostname=host) as ssock:
                cert = ssock.getpeercert()
                cipher = ssock.cipher()
        return {
            "host": host,
            "port": port,
            "subject": cert.get("subject"),
            "issuer": cert.get("issuer"),
            "not_before": cert.get("notBefore"),
            "not_after": cert.get("notAfter"),
            "cipher": cipher,
            "returncode": 0,
            "duration_seconds": round(time.time() - start, 3),
        }
    except Exception as exc:
        return {"host": host, "port": port, "returncode": 1, "error": str(exc), "duration_seconds": round(time.time() - start, 3)}


def action_scan_host(target: str, args: Dict[str, Any]) -> Dict[str, Any]:
    host = target_host(safe_target_string(target))
    check_action_allowed("scan_host", host)
    top_ports = int(args.get("top_ports") or POLICY.get("default_top_ports", 100))
    top_ports = max(1, min(top_ports, int(POLICY.get("max_top_ports", 200))))
    nmap = shutil.which("nmap") or "nmap"
    cmd = [
        nmap,
        "-Pn",
        "-sT",
        "-sV",
        "--version-light",
        "--top-ports",
        str(top_ports),
        "--reason",
        host,
    ]
    return run_cmd(cmd, timeout=int(POLICY.get("timeout_seconds", 90)))


def action_service_inventory(target: str, args: Dict[str, Any]) -> Dict[str, Any]:
    host = target_host(safe_target_string(target))
    check_action_allowed("service_inventory", host)
    out = {
        "dns_check": action_dns(host, {}),
        "ping": action_ping(host, {}),
        "scan_host": action_scan_host(host, args),
    }
    return {"returncode": 0, "inventory": out}


ACTIONS = {
    "ping": action_ping,
    "dns": action_dns,
    "dns_check": action_dns,
    "web": action_web_check,
    "web_check": action_web_check,
    "web_headers": action_web_check,
    "tls": action_tls_check,
    "tls_check": action_tls_check,
    "scan_host": action_scan_host,
    "inventory": action_service_inventory,
    "service_inventory": action_service_inventory,
}


def make_report(action: str, target: str, args: Dict[str, Any], result: Dict[str, Any]) -> Dict[str, str]:
    report_id = str(uuid.uuid4())
    json_path = REPORT_DIR / f"{report_id}.json"
    md_path = REPORT_DIR / f"{report_id}.md"
    doc = {
        "id": report_id,
        "created_at": now_iso(),
        "action": action,
        "target": target,
        "args": args,
        "policy_note": "authorized-security-lab-or-own-systems-only",
        "result": result,
    }
    json_path.write_text(json.dumps(doc, ensure_ascii=False, indent=2), encoding="utf-8")
    md = [
        f"# Kali Orchestrator Report {report_id}",
        "",
        f"- Created: `{doc['created_at']}`",
        f"- Action: `{action}`",
        f"- Target: `{target}`",
        f"- Return code: `{result.get('returncode', 'n/a')}`",
        "",
        "## Summary",
        "",
        summarize_result(result),
        "",
        "## Raw result",
        "",
        "```json",
        json.dumps(result, ensure_ascii=False, indent=2)[:12000],
        "```",
    ]
    md_path.write_text("\n".join(md), encoding="utf-8")
    return {"report_id": report_id, "json": str(json_path), "markdown": str(md_path)}


def summarize_result(result: Dict[str, Any]) -> str:
    if "stdout" in result:
        text = str(result.get("stdout") or "").strip()
        return text[:1500] if text else "No stdout."
    if "inventory" in result:
        return "Inventory completed. See raw result for DNS, ping and service scan."
    if "headers" in result:
        return f"HTTP {result.get('status_code')} from {result.get('final_url')} server={result.get('server')}"
    if "resolved_ips" in result:
        return "Resolved IPs: " + ", ".join(result.get("resolved_ips") or [])
    if "cipher" in result or "not_after" in result:
        return f"TLS certificate valid until {result.get('not_after')} with cipher {result.get('cipher')}"
    return json.dumps(result, ensure_ascii=False)[:1500]


@APP.get("/health")
def health():
    return jsonify({"ok": True, "service": "kali-security-orchestrator", "actions": sorted(ACTIONS.keys()), "time": now_iso()})


@APP.get("/actions")
def actions():
    deny = require_auth()
    if deny:
        return deny
    return jsonify({"ok": True, "actions": sorted(ACTIONS.keys()), "blocked_actions": POLICY.get("blocked_actions", [])})


@APP.post("/run")
def run_action():
    deny = require_auth()
    if deny:
        return deny
    data = request.get_json(force=True, silent=False) or {}
    action = str(data.get("action") or "").strip().lower()
    target = str(data.get("target") or "").strip()
    args = data.get("args") or {}

    if action not in ACTIONS:
        return jsonify({"ok": False, "error": f"unknown or blocked action: {action}", "allowed_actions": sorted(ACTIONS.keys())}), 400
    if not target:
        return jsonify({"ok": False, "error": "missing target"}), 400

    try:
        result = ACTIONS[action](target, args)
        report = make_report(action, target, args, result)
        return jsonify({"ok": result.get("returncode", 0) == 0, "action": action, "target": target, "result": result, "report": report})
    except PermissionError as exc:
        return jsonify({"ok": False, "error": str(exc), "policy": POLICY}), 403
    except subprocess.TimeoutExpired:
        return jsonify({"ok": False, "error": "action timeout"}), 504
    except Exception as exc:
        return jsonify({"ok": False, "error": str(exc)}), 500


@APP.get("/reports/<report_id>.json")
def report_json(report_id: str):
    deny = require_auth()
    if deny:
        return deny
    path = REPORT_DIR / f"{report_id}.json"
    if not path.exists():
        return jsonify({"ok": False, "error": "not found"}), 404
    return send_file(path)


@APP.get("/reports/<report_id>.md")
def report_md(report_id: str):
    deny = require_auth()
    if deny:
        return deny
    path = REPORT_DIR / f"{report_id}.md"
    if not path.exists():
        return jsonify({"ok": False, "error": "not found"}), 404
    return send_file(path, mimetype="text/markdown")


if __name__ == "__main__":
    APP.run(host=str(CONFIG["host"]), port=int(CONFIG["port"]))
