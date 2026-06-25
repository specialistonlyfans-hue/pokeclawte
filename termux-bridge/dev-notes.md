# Developer notes

Keep the bridge boring.

Good:

```text
local HTTP endpoint
Android intent launcher
Telegram polling trigger
small state file
```

Avoid for now:

```text
public inbound server
root-only features
APK refactor before smoke test
model work before bridge works
```

The first green test is:

```text
Termux command opens PokeClaw and PokeClaw runs a visible task.
```
