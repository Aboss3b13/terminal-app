# Terminal App

Mobile-first Android control panel for a Linux VPS.

The app connects over SSH/SFTP and turns common server work into simple screens:

- Android-style app launcher for Codex, Terminal, Files, System, Docker, and Settings
- Quick command buttons for status, disk, Docker, services, and custom commands
- File drawer using SFTP
- Dedicated Codex app with install/update, doctor, version, and prompt controls
- Saved connection settings on the device, kept inside Settings

## Security

The VPS password is intentionally not stored in this repository. Enter it inside the app at runtime.

## Build APK

GitHub Actions builds a debug APK on every push to `main`.

1. Open the repository on GitHub.
2. Go to **Actions**.
3. Open the latest **Build Android APK** run.
4. Download the `terminal-app-debug-apk` artifact.

Local build, if Android SDK and Gradle are installed:

```powershell
gradle assembleDebug
```

The APK will be created at:

```text
app/build/outputs/apk/debug/app-debug.apk
```
