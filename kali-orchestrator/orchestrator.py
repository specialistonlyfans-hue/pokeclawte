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
WORKFLOW_DIR = ROOT / "workflows"
DATA_DIR = ROOT / "storage"
TARGETS_PATH = DATA_DIR / "targets.json"
FINDINGS_PATH = DATA_DIR / "findings.jsonl"
JOBS_PATH = DATA_DIR / "jobs.jsonl"
REPORT_DIR.mkdir(exist_ok=True)
WORKFLOW_DIR.mkdir(exist_ok=True)
DATA_DIR.mkdir(exist_ok=True)

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


def read_json_file(path: Path, default: Any) -> Any:
    if not path.exists():
        return default
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return default


def write_json_file(path: Path, data: Any) -> None:
    path.parent.mkdir(exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")


def append_jsonl(path: Path, item: Dict[str, Any]) -> None:
    path.parent.mkdir(exist_ok=True)
    with path.open("a", encoding="utf-8") as handle:
        handle.write(json.dumps(item, ensure_ascii=False) + "\n")


def read_jsonl(path: Path, limit: int = 50) -> List[Dict[str, Any]]:
    if not path.exists():
        return []
    rows: List[Dict[str, Any]] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        try:
            rows.append(json.loads(line))
        except Exception:
            continue
    rows.reverse()
    return rows[: max(1, min(int(limit), 200))]


def list_targets() -> List[Dict[str, Any]]:
    targets = read_json_file(TARGETS_PATH, [])
    out = []
    for item in targets:
        value = str(item.get("value", ""))
        allowed, reason = target_allowed(value) if value else (False, "empty target")
        copy = dict(item)
        copy["allowed"] = allowed
        copy["allow_reason"] = reason
        out.append(copy)
    return out


def add_target_record(data: Dict[str, Any]) -> Dict[str, Any]:
    value = safe_target_string(str(data.get("value") or data.get("target") or ""))
    name = str(data.get("name") or value).strip()[:80]
    target_type = str(data.get("type") or "host").strip()[:40]
    notes = str(data.get("notes") or "").strip()[:500]
    allowed, reason = target_allowed(value)
    record = {
        "id": str(uuid.uuid4()),
        "name": name,
        "value": value,
        "type": target_type,
        "notes": notes,
        "created_at": now_iso(),
        "allowed": allowed,
        "allow_reason": reason,
    }
    targets = read_json_file(TARGETS_PATH, [])
    targets = [item for item in targets if item.get("value") != value]
    targets.append(record)
    write_json_file(TARGETS_PATH, targets)
    return record


def list_workflows() -> Dict[str, Any]:
    items: Dict[str, Any] = {}
    for path in sorted(WORKFLOW_DIR.glob("*.json")):
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
            items[path.stem] = {
                "name": data.get("name", path.stem),
                "description": data.get("description", ""),
                "steps": [step.get("action") for step in data.get("steps", [])],
            }
        except Exception as exc:
            items[path.stem] = {"error": str(exc)}
    return items


def load_workflow(name: str) -> Dict[str, Any]:
    safe_name = re.sub(r"[^a-zA-Z0-9_\-]", "", name or "")
    if not safe_name:
        raise ValueError("invalid workflow name")
    path = WORKFLOW_DIR / f"{safe_name}.json"
    if not path.exists():
        raise FileNotFoundError(f"workflow not found: {safe_name}")
    return json.loads(path.read_text(encoding="utf-8"))


def run_workflow(name: str, target: str, args: Dict[str, Any]) -> Dict[str, Any]:
    workflow = load_workflow(name)
    target = safe_target_string(target)
    base_args = dict(workflow.get("default_args") or {})
    base_args.update(args or {})
    steps_out: List[Dict[str, Any]] = []
    start = time.time()

    for step in workflow.get("steps", []):
        step_id = str(step.get("id") or step.get("action") or "step")
        action = str(step.get("action") or "").strip().lower()
        step_target = str(step.get("target") or target)
        step_args = dict(base_args)
        step_args.update(step.get("args") or {})
        required = bool(step.get("required", False))

        if action not in ACTIONS:
            item = {"id": step_id, "action": action, "ok": False, "error": "unknown action"}
            steps_out.append(item)
            if required:
                break
            continue

        try:
            result = ACTIONS[action](step_target, step_args)
            ok = result.get("returncode", 0) == 0
            steps_out.append({"id": step_id, "action": action, "target": step_target, "ok": ok, "result": result})
            if required and not ok:
                break
        except Exception as exc:
            steps_out.append({"id": step_id, "action": action, "target": step_target, "ok": False, "error": str(exc)})
            if required:
                break

    ok_count = sum(1 for item in steps_out if item.get("ok"))
    return {
        "returncode": 0 if ok_count > 0 else 1,
        "workflow": workflow.get("name", name),
        "description": workflow.get("description", ""),
        "target": target,
        "steps_total": len(steps_out),
        "steps_ok": ok_count,
        "duration_seconds": round(time.time() - start, 3),
        "steps": steps_out,
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
    write_findings_from_report(doc)
    return {"report_id": report_id, "json": str(json_path), "markdown": str(md_path)}


def summarize_result(result: Dict[str, Any]) -> str:
    if "workflow" in result:
        return f"Workflow {result.get('workflow')} completed: {result.get('steps_ok')}/{result.get('steps_total')} steps OK."
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


def finding_record(report_id: str, target: str, title: str, severity: str, evidence: str) -> Dict[str, Any]:
    return {
        "id": str(uuid.uuid4()),
        "report_id": report_id,
        "created_at": now_iso(),
        "target": target,
        "title": title,
        "severity": severity,
        "status": "open",
        "evidence": evidence[:1200],
    }


def write_findings_from_report(doc: Dict[str, Any]) -> None:
    report_id = str(doc.get("id", ""))
    target = str(doc.get("target", ""))
    result = doc.get("result") or {}
    findings: List[Dict[str, Any]] = []

    def add(title: str, severity: str, evidence: str) -> None:
        findings.append(finding_record(report_id, target, title, severity, evidence))

    if "workflow" in result:
        for step in result.get("steps") or []:
            if not step.get("ok"):
                add(f"Workflow step failed: {step.get('action')}", "info", str(step.get("error") or step.get("result") or "step failed"))
            step_result = step.get("result") or {}
            if isinstance(step_result, dict):
                extract_result_findings(step_result, add)
    else:
        extract_result_findings(result, add)

    for item in findings:
        append_jsonl(FINDINGS_PATH, item)


def extract_result_findings(result: Dict[str, Any], add) -> None:
    if result.get("returncode") not in (0, None):
        add("Check returned an error", "info", str(result.get("error") or result.get("stderr") or result)[:1200])
    headers = result.get("headers")
    if isinstance(headers, dict):
        lower = {str(k).lower(): str(v) for k, v in headers.items()}
        if "content-security-policy" not in lower:
            add("Missing Content-Security-Policy header", "low", "HTTP response headers did not include Content-Security-Policy.")
        if "x-frame-options" not in lower:
            add("Missing X-Frame-Options header", "low", "HTTP response headers did not include X-Frame-Options.")
        if "strict-transport-security" not in lower and str(result.get("final_url", "")).startswith("https://"):
            add("Missing HSTS header", "low", "HTTPS response did not include Strict-Transport-Security.")
    stdout = str(result.get("stdout") or "")
    open_lines = [line.strip() for line in stdout.splitlines() if " open " in line.lower()]
    for line in open_lines[:20]:
        add("Open network service observed", "info", line)


def report_summary(path: Path) -> Dict[str, Any]:
    try:
        doc = json.loads(path.read_text(encoding="utf-8"))
        result = doc.get("result") or {}
        stat = path.stat()
        return {
            "id": doc.get("id", path.stem),
            "created_at": doc.get("created_at", ""),
            "action": doc.get("action", ""),
            "target": doc.get("target", ""),
            "returncode": result.get("returncode", "n/a"),
            "summary": summarize_result(result),
            "json_path": str(path),
            "markdown_path": str(REPORT_DIR / f"{path.stem}.md"),
            "size_bytes": stat.st_size,
            "mtime": int(stat.st_mtime),
        }
    except Exception as exc:
        return {"id": path.stem, "error": str(exc), "json_path": str(path)}


def list_reports(limit: int = 25) -> List[Dict[str, Any]]:
    limit = max(1, min(int(limit), 100))
    paths = sorted(REPORT_DIR.glob("*.json"), key=lambda p: p.stat().st_mtime, reverse=True)
    return [report_summary(path) for path in paths[:limit]]


def list_evidence(limit: int = 50) -> List[Dict[str, Any]]:
    items: List[Dict[str, Any]] = []
    for path in sorted(REPORT_DIR.glob("*.*"), key=lambda p: p.stat().st_mtime, reverse=True)[: max(1, min(limit, 200))]:
        stat = path.stat()
        items.append({
            "id": path.stem,
            "name": path.name,
            "path": str(path),
            "type": path.suffix.lstrip("."),
            "size_bytes": stat.st_size,
            "mtime": int(stat.st_mtime),
        })
    return items


def record_job(kind: str, target: str, status: str, report: Dict[str, str], extra: Dict[str, Any]) -> None:
    append_jsonl(JOBS_PATH, {
        "id": str(uuid.uuid4()),
        "created_at": now_iso(),
        "kind": kind,
        "target": target,
        "status": status,
        "report_id": report.get("report_id"),
        "extra": extra,
    })


@APP.get("/health")
def health():
    return jsonify({"ok": True, "service": "kali-security-orchestrator", "actions": sorted(ACTIONS.keys()), "workflows": sorted(list_workflows().keys()), "time": now_iso()})


@APP.get("/actions")
def actions():
    deny = require_auth()
    if deny:
        return deny
    return jsonify({"ok": True, "actions": sorted(ACTIONS.keys()), "blocked_actions": POLICY.get("blocked_actions", [])})


@APP.get("/targets")
def targets_index():
    deny = require_auth()
    if deny:
        return deny
    return jsonify({"ok": True, "targets": list_targets()})


@APP.post("/targets")
def targets_add():
    deny = require_auth()
    if deny:
        return deny
    data = request.get_json(force=True, silent=False) or {}
    try:
        record = add_target_record(data)
        return jsonify({"ok": True, "target": record})
    except Exception as exc:
        return jsonify({"ok": False, "error": str(exc)}), 400


@APP.get("/workflows")
def workflows():
    deny = require_auth()
    if deny:
        return deny
    return jsonify({"ok": True, "workflows": list_workflows()})


@APP.get("/reports")
def reports_index():
    deny = require_auth()
    if deny:
        return deny
    limit = request.args.get("limit", "25")
    try:
        limit_int = int(limit)
    except ValueError:
        limit_int = 25
    return jsonify({"ok": True, "reports": list_reports(limit_int)})


@APP.get("/findings")
def findings_index():
    deny = require_auth()
    if deny:
        return deny
    limit = int(request.args.get("limit", "50") or 50)
    return jsonify({"ok": True, "findings": read_jsonl(FINDINGS_PATH, limit)})


@APP.get("/evidence")
def evidence_index():
    deny = require_auth()
    if deny:
        return deny
    limit = int(request.args.get("limit", "50") or 50)
    return jsonify({"ok": True, "evidence": list_evidence(limit)})


@APP.get("/jobs")
def jobs_index():
    deny = require_auth()
    if deny:
        return deny
    limit = int(request.args.get("limit", "50") or 50)
    return jsonify({"ok": True, "jobs": read_jsonl(JOBS_PATH, limit)})


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
        ok = result.get("returncode", 0) == 0
        record_job("action", target, "ok" if ok else "error", report, {"action": action})
        return jsonify({"ok": ok, "action": action, "target": target, "result": result, "report": report})
    except PermissionError as exc:
        return jsonify({"ok": False, "error": str(exc), "policy": POLICY}), 403
    except subprocess.TimeoutExpired:
        return jsonify({"ok": False, "error": "action timeout"}), 504
    except Exception as exc:
        return jsonify({"ok": False, "error": str(exc)}), 500


@APP.post("/workflow")
def run_workflow_endpoint():
    deny = require_auth()
    if deny:
        return deny
    data = request.get_json(force=True, silent=False) or {}
    name = str(data.get("workflow") or data.get("name") or "").strip()
    target = str(data.get("target") or "").strip()
    args = data.get("args") or {}

    if not name:
        return jsonify({"ok": False, "error": "missing workflow"}), 400
    if not target:
        return jsonify({"ok": False, "error": "missing target"}), 400

    try:
        result = run_workflow(name, target, args)
        report = make_report(f"workflow:{name}", target, args, result)
        ok = result.get("returncode", 0) == 0
        record_job("workflow", target, "ok" if ok else "error", report, {"workflow": name})
        return jsonify({"ok": ok, "workflow": name, "target": target, "result": result, "report": report})
    except PermissionError as exc:
        return jsonify({"ok": False, "error": str(exc), "policy": POLICY}), 403
    except FileNotFoundError as exc:
        return jsonify({"ok": False, "error": str(exc), "workflows": list_workflows()}), 404
    except subprocess.TimeoutExpired:
        return jsonify({"ok": False, "error": "workflow timeout"}), 504
    except Exception as exc:
        return jsonify({"ok": False, "error": str(exc)}), 500


@APP.get("/reports/<report_id>.json")
def report_json(report_id: str):
    deny = require_auth()
    if deny:
        return deny
    safe_id = re.sub(r"[^a-zA-Z0-9_\-]", "", report_id or "")
    path = REPORT_DIR / f"{safe_id}.json"
    if not path.exists():
        return jsonify({"ok": False, "error": "not found"}), 404
    return send_file(path)


@APP.get("/reports/<report_id>.md")
def report_md(report_id: str):
    deny = require_auth()
    if deny:
        return deny
    safe_id = re.sub(r"[^a-zA-Z0-9_\-]", "", report_id or "")
    path = REPORT_DIR / f"{safe_id}.md"
    if not path.exists():
        return jsonify({"ok": False, "error": "not found"}), 404
    return send_file(path, mimetype="text/markdown")


if __name__ == "__main__":
    APP.run(host=str(CONFIG["host"]), port=int(CONFIG["port"]))
