# Telegram guide

This uses Telegram long polling from Termux. No webhook and no public server.

## Start

```bash
export TELEGRAM_BOT_TOKEN="PUT_TOKEN_HERE"
./start.sh
./run-telegram.sh
```

Background mode:

```bash
export TELEGRAM_BOT_TOKEN="PUT_TOKEN_HERE"
./start.sh
./start-telegram-background.sh
```

Optional lock to your own chat:

```bash
export TELEGRAM_ALLOWED_CHAT_ID="123456789"
```

## Commands

```text
/task how much battery left
/chat what can you do?
```

Normal messages are treated as tasks.

## Current result behavior

Telegram receives launch confirmation. Final PokeClaw answer is currently visible inside PokeClaw, not automatically returned to Telegram. That needs the later APK callback work.
