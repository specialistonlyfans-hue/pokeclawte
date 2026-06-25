# Safety model

The orchestrator is built around three gates:

```text
1. Action must be implemented in ACTIONS
2. Target must match allowed_targets
3. Command must pass blocked keyword check
```

There is no endpoint for arbitrary shell commands.

## Why this matters

A phone/Telegram-controlled Kali box is powerful. Without policy, it is too easy to run the wrong thing against the wrong target.

## Good first use cases

```text
inventory own router
inventory own lab VM
check web headers on own service
create simple report
```

## Later lab-only expansion

Add any stronger test as a separate module with:

```text
written scope file
lab_mode=true
confirmation token
target allowlist
rate limit
full report
manual review
```
