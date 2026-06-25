# Policy

This orchestrator is designed for authorized labs and your own systems.

## Default allowed actions

```text
ping
DNS resolve
HTTP header check
limited service inventory
combined inventory
```

## Restricted categories

The first version does not automate high-risk offensive actions. Keep them as manual, scoped professional work with written permission, explicit confirmation, rate limits, logging, and reporting.

## Target allowlist

Only targets in `allowed_targets` run.

Examples:

```json
"allowed_targets": [
  "192.168.56.0/24",
  "10.10.10.0/24",
  "scanme.nmap.org"
]
```

## No arbitrary shell

The API never accepts free shell commands. Every action maps to a fixed command template.
