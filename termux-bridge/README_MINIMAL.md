# Minimal

After APK install and External Automation enabled:

```bash
cd $HOME/pokeclawte/termux-bridge
chmod +x *.sh *.py
./install.sh
./direct-am-test.sh "how much battery left"
./start.sh
./test.sh
```

Telegram optional:

```bash
export TELEGRAM_BOT_TOKEN="PUT_TOKEN_HERE"
./start-telegram-background.sh
```
