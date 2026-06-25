# Android Debug Build

Build locally:

```bash
chmod +x ./gradlew
./gradlew :app:assembleDebug
```

APK output:

```text
app/build/outputs/apk/debug/
```

Install with adb:

```bash
adb install -r app/build/outputs/apk/debug/*.apk
```

After install, open the app entry named `Kali Lab` to use the new cockpit UI.
