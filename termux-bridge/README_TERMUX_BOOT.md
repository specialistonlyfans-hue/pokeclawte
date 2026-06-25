# Termux:Boot

Optional auto-start after phone boot.

Install Termux:Boot from F-Droid, open it once, then:

```bash
cd $HOME/pokeclawte/termux-bridge
./termux-boot-install.sh
```

It installs:

```text
$HOME/.termux/boot/pokeclaw-bridge
```

The boot script waits 10 seconds, then starts the bridge.

Telegram autostart is only useful if `TELEGRAM_BOT_TOKEN` is exported in your Termux environment or added to the boot script later.
