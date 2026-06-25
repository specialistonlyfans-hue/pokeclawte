# Security note

The bridge can launch phone automation tasks. Keep it local by default.

Default binding:

```text
127.0.0.1:8787
```

Do not expose this port to the public internet.

If you change the host to `0.0.0.0`, set `bridge_token` in `config.json` and send it with:

```text
X-Bridge-Token: YOUR_TOKEN
```

Safer setup:

```text
Telegram polling bot -> local bridge -> PokeClaw
```

No inbound port is needed for Telegram polling.
