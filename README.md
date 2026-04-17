# Loot'em Shoot'em

Multiplayer top-down shooter built with LibGDX (client) and Java WebSocket (server).

Players join from a shared server URL, fight over weapon pickups and health packs, and compete on a live kill leaderboard. Supports desktop and Android.

---

## Requirements

- Java 17

```bash
java --version   # must show 17.x
```

Install on Ubuntu if missing:
```bash
sudo apt update && sudo apt install openjdk-17-jdk
```

For Android builds, Android Studio + Android SDK are also required.

On Windows, Gradle can be pinned to a local Java 17 install with:
```properties
org.gradle.java.home=C:\\Path\\To\\jdk-17
org.gradle.java.installations.paths=C:\\Path\\To\\jdk-17
```

---

## Running

Server and client must run in **separate terminals** from the `LootemShootem/` folder.

**Terminal 1 - Server**
```bash
# Linux
./gradlew :server:run

# Windows
.\gradlew.bat :server:run
```

Server starts on `ws://localhost:8080/ws` by default. Port and tick rate can be changed in `server/src/main/resources/server.conf`.

**Terminal 2 - Desktop client**
```bash
# Linux
./gradlew :desktop:run

# Windows
.\gradlew.bat :desktop:run
```

Enter the server URL and a username on the main menu, then hit **Connect**. Open more client windows to add more players.

**Android client**
```bash
# Windows
.\gradlew.bat :android:assembleDebug
.\gradlew.bat :android:installDebug
```

You can also run the `android` module directly from Android Studio after Gradle sync completes.

For local Android testing:

- with USB + `adb reverse`, use `ws://127.0.0.1:8080/ws`
- over Wi-Fi / hotspot, use `ws://<PC_IP>:8080/ws`

To forward the local server through USB:
```bash
adb reverse tcp:8080 tcp:8080
```

---

## Controls

| Action | Desktop | Android |
|--------|---------|---------|
| Move | WASD | Movement stick |
| Aim | Mouse | Aim stick |
| Shoot | Mouse (left click) | `FIRE` button |
| Single shot | - | Tap `FIRE` |
| Continuous fire | - | Hold `FIRE` |
| Switch weapon | Space | `SWP` |
| Reload | R | `RLD` |
| Pause / back to menu | Escape | Top-left pause button |

On Android, pause menu options include:

- music mute / volume slider
- joystick size slider
- joystick opacity slider
- right-handed / left-handed layout swap
- controls help panel

---

## Gameplay

- Pick up weapons, health packs, and speed boosts scattered around the map
- You carry up to **2 weapons** - switching is instant, no reload delay
- Dying drops your secondary weapon for others to grab
- Health regenerates slowly while alive
- Kill feed appears top-left; leaderboard top-right
- Weapons now use per-character hand placement for cleaner visual alignment
- Bullets, flames, and crossbow arrows were adjusted for better contrast on bright floor tiles

---

## Project layout

```
LootemShootem/
  server/     authoritative game server (WebSocket, 20 Hz tick loop)
  core/       shared client code (LibGDX, MVC)
  desktop/    desktop launcher (LWJGL3)
  android/    Android launcher
  shared/     DTOs and protocol shared between client and server
```

---

## Troubleshooting

**Port 8080 already in use**
```bash
# Linux
sudo fuser -k 8080/tcp

# Windows
netstat -ano | findstr :8080
taskkill /PID <PID> /F
```

**Android cannot connect to a local server**
```bash
# USB workflow
adb reverse tcp:8080 tcp:8080
```

Then connect with:
```text
ws://127.0.0.1:8080/ws
```

If testing over Wi-Fi, make sure:

- the server is running in a separate terminal
- the server URL ends with `/ws`
- the phone and PC are on a network that allows device-to-device traffic
- Windows Firewall allows inbound TCP on port `8080`

Some campus / guest / public networks isolate devices from each other. In that case Android local testing may only work with USB `adb reverse`, a hotspot, or an externally hosted server.

**Android Studio cannot find Java 17**
```properties
org.gradle.java.home=C:\\Path\\To\\jdk-17
org.gradle.java.installations.paths=C:\\Path\\To\\jdk-17
```

**Permission denied on gradlew (Linux)**
```bash
chmod +x ./gradlew
```
