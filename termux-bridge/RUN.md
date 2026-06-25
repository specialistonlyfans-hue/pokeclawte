# Run

In Termux:

```bash
pkg update -y
pkg install -y git
cd $HOME
git clone https://github.com/specialistonlyfans-hue/pokeclawte.git
cd pokeclawte/termux-bridge
chmod +x *.sh *.py
./install.sh
./direct-am-test.sh "how much battery left"
./start.sh
./test.sh
```

Telegram mode:

```bash
export TELEGRAM_BOT_TOKEN="PUT_TOKEN_HERE"
./run-telegram.sh
```
