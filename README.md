# MyTopDownShooter (MVP)
libGDX (client) and Java WebSocket (server)

# Linux (Ubuntu / Pop!_OS)
java --version
(Must show Java 11)

# Install Java 11 if missing
sudo apt update
sudo apt install openjdk-11-jdk

# Windows (Git Bash recommended)
java --version
(Must show Java 11)


git clone [<YOUR_REPO_URL>](https://github.com/kristiancarlenius/TDT4240-game.git)
cd TDT4240-game/MyTopDownShooter

# OR unzip archive and:
cd TDT4240-game/MyTopDownShooter

# IMPORTANT:
# Run server and client in separate terminals

# ---------- TERMINAL 1 (SERVER) ----------

# Linux
./gradlew clean :server:run

# Windows (Git Bash)
./gradlew.bat clean :server:run


# ---------- TERMINAL 2 (CLIENT) ----------

# Linux
./gradlew :desktop:run

# Windows (Git Bash)
./gradlew.bat :desktop:run

Run :desktop:run multiple times to open multiple clients


# List projects
./gradlew projects

# List server tasks
./gradlew :server:tasks

# List desktop tasks
./gradlew :desktop:tasks

# Clean build
./gradlew clean


# Port 8080 already in use (Linux)
sudo lsof -i :8080
sudo kill -9 <PID>
sudo fuser -k 8080/tcp

# Port 8080 already in use (Windows Git Bash)
netstat -ano | findstr :8080
taskkill /PID <PID> /F

# gradlew permission denied (Linux)
chmod +x ./gradlew

