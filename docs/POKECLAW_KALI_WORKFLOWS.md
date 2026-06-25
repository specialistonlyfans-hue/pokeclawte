# PokeClaw Kali Workflows

PokeClaw can now call Kali Orchestrator workflows directly from chat.

## Commands

```text
/kali workflow quick_host 192.168.1.20 50
/kali workflow web_audit http://192.168.1.20 50
```

Short alias:

```text
/kali wf quick_host 192.168.1.20 50
```

## Current workflows

```text
quick_host = dns_check + ping + web_check + tls_check + scan_host
web_audit  = web_check + tls_check + scan_host
```

## Server API

```text
GET  /workflows
POST /workflow
```

The same token and target allowlist checks still apply.
