# Loot'em Shoot'em

Multiplayer top-down shooter built with LibGDX (client) and Java WebSocket (server).

Players join from a shared server URL, fight over weapon pickups and health packs, and compete on a live kill leaderboard. Supports desktop and Android.

---

## Requirements

- Java 11

```bash
java --version   # must show 17.x
```

Install on Ubuntu if missing:
```bash
sudo apt update && sudo apt install openjdk-17-jdk
```

---

## Running

Server and client must run in **separate terminals** from the `LootemShootem/` folder.

**Terminal 1 — Server**
```bash
# Linux
./gradlew :server:run

# Windows (Git Bash)
./gradlew.bat :server:run
```

Server starts on `ws://localhost:8080/ws` by default. Port and tick rate can be changed in `server/src/main/resources/server.conf`.

**Terminal 2 — Client**
```bash
# Linux
./gradlew :desktop:run

# Windows
./gradlew.bat :desktop:run
```

Enter the server URL and a username on the main menu, then hit **Connect**. Open more client windows to add more players.

---

## Controls

| Action | Desktop |
|--------|---------|
| Move | WASD |
| Aim & shoot | Mouse (left click) |
| Switch weapon | Space |
| Back to menu | Escape |

On Android, move and aim joysticks appear on screen. Tap **SW** to switch weapons.

---

## Gameplay

- Pick up weapons, health packs, and speed boosts scattered around the map
- You carry up to **2 weapons** — switching is instant, no reload delay
- Dying drops your secondary weapon for others to grab
- Health regenerates slowly while alive
- Kill feed appears top-left; leaderboard top-right

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

**Permission denied on gradlew (Linux)**
```bash
chmod +x ./gradlew
```
