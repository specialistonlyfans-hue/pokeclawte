# Quickstart

APK later. Bridge now.

## 1. Install PokeClaw APK later and enable setting

```text
Settings -> Remote Control -> External Automation
```

## 2. Clone repo in Termux

```bash
pkg install -y git
cd $HOME
git clone https://github.com/specialistonlyfans-hue/pokeclawte.git
cd pokeclawte/termux-bridge
```

## 3. Install bridge

```bash
chmod +x install.sh
./install.sh
```

## 4. First direct smoke test

```bash
./direct-am-test.sh "how much battery left"
```

If PokeClaw opens, the Android intent path works.

## 5. Start local bridge

```bash
./start.sh
```

## 6. Test bridge

```bash
./test.sh
```

## 7. Optional Telegram control

```bash
export TELEGRAM_BOT_TOKEN="123456:ABC..."
./run-telegram.sh
```

Send your bot:

```text
/task how much battery left
```

## 8. Keep alive

```bash
termux-wake-lock
```

Set Android battery mode for Termux and PokeClaw to unrestricted.
