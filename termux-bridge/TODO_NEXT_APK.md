# Next APK work

Do this later, not now.

## 1. Add HTTP callback sender in PokeClaw

When a task reaches terminal state, send:

```json
{
  "request_id": "...",
  "status": "completed",
  "result": "...",
  "error": null
}
```

to:

```text
http://127.0.0.1:8787/callback
```

## 2. Add `/callback` endpoint to bridge.py

Store final task result in state.json and forward to Telegram if the request came from Telegram.

## 3. Add request tracking

Telegram message -> request_id -> PokeClaw task -> callback -> Telegram reply.

## 4. Keep safety

Do not expose bridge to public internet. Keep localhost default.
