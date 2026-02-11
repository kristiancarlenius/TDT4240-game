from __future__ import annotations

import os
import sys
from pathlib import Path
from dataclasses import dataclass
from typing import Iterable, Dict, Tuple

OVERWRITE = False

# ---------- helpers ----------

@dataclass(frozen=True)
class FileSpec:
    relpath: str
    content: str = ""

def ensure_dir(path: Path) -> None:
    path.mkdir(parents=True, exist_ok=True)

def write_file(path: Path, content: str) -> None:
    if path.exists() and not OVERWRITE:
        return
    ensure_dir(path.parent)
    path.write_text(content, encoding="utf-8")

def kotlin_placeholder(package: str, classname: str, body: str = "") -> str:
    return f"""package {package}

class {classname} {{
{body.rstrip() if body.strip() else "    // TODO: implement"}
}}
"""

def kotlin_object_placeholder(package: str, name: str, body: str = "") -> str:
    return f"""package {package}

object {name} {{
{body.rstrip() if body.strip() else "    // TODO: implement"}
}}
"""

def kotlin_interface_placeholder(package: str, name: str, body: str = "") -> str:
    return f"""package {package}

interface {name} {{
{body.rstrip() if body.strip() else "    // TODO: define methods"}
}}
"""

# ---------- structure spec ----------

def build_structure(project_name: str) -> Tuple[Iterable[Path], Iterable[FileSpec]]:
    # Top-level dirs
    dirs = [
        Path("gradle"),
        Path("android"),
        Path("core"),
        Path("server"),
        Path("shared"),
    ]

    # Common base packages
    shared_pkg = "com.mygame.shared"
    client_pkg = "com.mygame.client"
    android_pkg = "com.mygame.android"
    server_pkg = "com.mygame.server"

    files: list[FileSpec] = []

    # Top-level gradle placeholders (you will likely replace these)
    files += [
        FileSpec("settings.gradle.kts", f'rootProject.name = "{project_name}"\n'),
        FileSpec("build.gradle.kts", "// TODO: configure multi-module Gradle build\n"),
        FileSpec("README.md", f"# {project_name}\n\nGenerated skeleton.\n"),
    ]

    # ---------------- shared ----------------
    shared_base = Path("shared/src/main/kotlin") / Path(*shared_pkg.split("."))
    shared_files = [
        (shared_base / "protocol/ClientMessage.kt", f"""package {shared_pkg}.protocol

sealed interface ClientMessage
"""),
        (shared_base / "protocol/ServerMessage.kt", f"""package {shared_pkg}.protocol

sealed interface ServerMessage
"""),
        (shared_base / "protocol/MessageCodec.kt", f"""package {shared_pkg}.protocol

object MessageCodec {{
    // TODO: implement JSON encode/decode (kotlinx.serialization recommended)
}}
"""),
        (shared_base / "protocol/ProtocolConstants.kt", f"""package {shared_pkg}.protocol

object ProtocolConstants {{
    const val TICK_RATE_HZ: Int = 20
}}
"""),
        (shared_base / "dto/PlayerDto.kt", f"""package {shared_pkg}.dto

data class PlayerDto(
    val id: String,
    val x: Float,
    val y: Float,
    val hp: Int
)
"""),
        (shared_base / "dto/ProjectileDto.kt", f"""package {shared_pkg}.dto

data class ProjectileDto(
    val id: String,
    val x: Float,
    val y: Float
)
"""),
        (shared_base / "dto/PickupDto.kt", f"""package {shared_pkg}.dto

data class PickupDto(
    val id: String,
    val type: String,
    val x: Float,
    val y: Float
)
"""),
        (shared_base / "dto/MapDto.kt", f"""package {shared_pkg}.dto

data class MapDto(
    val width: Int,
    val height: Int,
    val tiles: List<Int>
)
"""),
        (shared_base / "dto/WeaponDto.kt", f"""package {shared_pkg}.dto

data class WeaponDto(
    val id: String,
    val damage: Int,
    val fireRate: Float,
    val spread: Float,
    val projectileSize: Float,
    val ammo: Int
)
"""),
        (shared_base / "util/Vec2.kt", f"""package {shared_pkg}.util

data class Vec2(val x: Float, val y: Float)
"""),
        (shared_base / "util/Id.kt", f"""package {shared_pkg}.util

object Id {{
    fun newId(): String = java.util.UUID.randomUUID().toString()
}}
"""),
    ]
    for p, c in shared_files:
        files.append(FileSpec(str(p), c))

    # ---------------- core (client) ----------------
    core_base = Path("core/src/main/kotlin") / Path(*client_pkg.split("."))

    # Root entry
    files.append(FileSpec(str(core_base / "GameApp.kt"),
        f"""package {client_pkg}

import com.badlogic.gdx.Game

class GameApp : Game() {{
    override fun create() {{
        // TODO: set initial screen via Navigator/ScreenFactory
    }}
}}
"""))

    # di
    files += [
        FileSpec(str(core_base / "di/AppModule.kt"), kotlin_object_placeholder(f"{client_pkg}.di", "AppModule")),
        FileSpec(str(core_base / "di/ScreenFactory.kt"), kotlin_object_placeholder(f"{client_pkg}.di", "ScreenFactory")),
    ]

    # presentation navigation
    files += [
        FileSpec(str(core_base / "presentation/navigation/Routes.kt"),
                 kotlin_object_placeholder(f"{client_pkg}.presentation.navigation", "Routes",
                 '    const val MAIN_MENU = "main_menu"\n    const val LOBBY = "lobby"\n    const val GAME = "game"\n    const val RESULTS = "results"\n')),
        FileSpec(str(core_base / "presentation/navigation/Navigator.kt"),
                 kotlin_placeholder(f"{client_pkg}.presentation.navigation", "Navigator")),
    ]

    # screens (Views)
    for screen in ["MainMenuScreen", "LobbyScreen", "GameScreen", "ResultsScreen"]:
        files.append(FileSpec(
            str(core_base / f"presentation/screens/{screen}.kt"),
            f"""package {client_pkg}.presentation.screens

import com.badlogic.gdx.Screen

class {screen} : Screen {{
    override fun show() {{}}
    override fun render(delta: Float) {{}}
    override fun resize(width: Int, height: Int) {{}}
    override fun pause() {{}}
    override fun resume() {{}}
    override fun hide() {{}}
    override fun dispose() {{}}
}}
"""
        ))

    # view renderers
    for r in ["WorldRenderer", "HudRenderer", "DebugRenderer"]:
        files.append(FileSpec(
            str(core_base / f"presentation/view/renderer/{r}.kt"),
            kotlin_placeholder(f"{client_pkg}.presentation.view.renderer", r)
        ))

    # view input
    for v in ["VirtualJoystickView", "ActionButtonsView", "InputLayout"]:
        files.append(FileSpec(
            str(core_base / f"presentation/view/input/{v}.kt"),
            kotlin_placeholder(f"{client_pkg}.presentation.view.input", v)
        ))

    # view ui
    files += [
        FileSpec(str(core_base / "presentation/view/ui/SkinProvider.kt"),
                 kotlin_object_placeholder(f"{client_pkg}.presentation.view.ui", "SkinProvider")),
        FileSpec(str(core_base / "presentation/view/ui/Widgets.kt"),
                 kotlin_object_placeholder(f"{client_pkg}.presentation.view.ui", "Widgets")),
    ]

    # controllers
    for c in ["MainMenuController", "LobbyController", "GameController", "ResultsController"]:
        files.append(FileSpec(
            str(core_base / f"presentation/controller/{c}.kt"),
            kotlin_placeholder(f"{client_pkg}.presentation.controller", c)
        ))

    # presenters (optional)
    for p in ["GameHudPresenter", "PlayerPresenter"]:
        files.append(FileSpec(
            str(core_base / f"presentation/presenter/{p}.kt"),
            kotlin_placeholder(f"{client_pkg}.presentation.presenter", p)
        ))

    # application usecases
    for u in [
        "StartMatchUseCase",
        "JoinServerUseCase",
        "LeaveMatchUseCase",
        "SendInputUseCase",
        "ApplySnapshotUseCase",
    ]:
        files.append(FileSpec(
            str(core_base / f"application/usecase/{u}.kt"),
            kotlin_placeholder(f"{client_pkg}.application.usecase", u)
        ))

    # application services
    for s in ["GameSessionService", "MatchmakingService"]:
        files.append(FileSpec(
            str(core_base / f"application/service/{s}.kt"),
            kotlin_placeholder(f"{client_pkg}.application.service", s)
        ))

    # domain model
    for m in ["GameState", "Player", "Weapon", "Projectile", "Pickup", "Tile"]:
        files.append(FileSpec(
            str(core_base / f"domain/model/{m}.kt"),
            kotlin_placeholder(f"{client_pkg}.domain.model", m)
        ))

    # domain rules
    for r in ["CombatRules", "MovementRules", "PickupRules"]:
        files.append(FileSpec(
            str(core_base / f"domain/rules/{r}.kt"),
            kotlin_object_placeholder(f"{client_pkg}.domain.rules", r)
        ))

    # domain systems
    for s in ["WorldStepSystem", "CollisionSystem", "ProjectileSystem", "TrapSystem"]:
        files.append(FileSpec(
            str(core_base / f"domain/system/{s}.kt"),
            kotlin_placeholder(f"{client_pkg}.domain.system", s)
        ))

    # domain ports
    files += [
        FileSpec(str(core_base / "domain/ports/NetworkClientPort.kt"),
                 kotlin_interface_placeholder(f"{client_pkg}.domain.ports", "NetworkClientPort")),
        FileSpec(str(core_base / "domain/ports/MapRepositoryPort.kt"),
                 kotlin_interface_placeholder(f"{client_pkg}.domain.ports", "MapRepositoryPort")),
        FileSpec(str(core_base / "domain/ports/WeaponRepositoryPort.kt"),
                 kotlin_interface_placeholder(f"{client_pkg}.domain.ports", "WeaponRepositoryPort")),
        FileSpec(str(core_base / "domain/ports/PreferencesPort.kt"),
                 kotlin_interface_placeholder(f"{client_pkg}.domain.ports", "PreferencesPort")),
    ]

    # data network/repository/assets
    for f, kind in [
        ("data/network/NetClient.kt", "class"),
        ("data/network/WebSocketTransport.kt", "class"),
        ("data/network/NetMapper.kt", "object"),
        ("data/repository/MapRepository.kt", "class"),
        ("data/repository/WeaponRepository.kt", "class"),
        ("data/repository/PreferencesRepository.kt", "class"),
        ("data/assets/Assets.kt", "object"),
        ("data/assets/AssetLoader.kt", "class"),
    ]:
        pkg = f"{client_pkg}." + ".".join(Path(f).parts[:-1])
        name = Path(f).stem
        if kind == "object":
            content = kotlin_object_placeholder(pkg, name)
        else:
            content = kotlin_placeholder(pkg, name)
        files.append(FileSpec(str(core_base / f), content))

    # util
    for u in ["Time", "Logging"]:
        files.append(FileSpec(
            str(core_base / f"util/{u}.kt"),
            kotlin_object_placeholder(f"{client_pkg}.util", u)
        ))

    # core resources (assets)
    files += [
        FileSpec("core/src/main/resources/maps/map01.json", "{\n  \"width\": 10,\n  \"height\": 10,\n  \"tiles\": []\n}\n"),
        FileSpec("core/src/main/resources/weapons/weapons.json", "{\n  \"weapons\": []\n}\n"),
    ]
    # Make dirs for textures/sounds/ui without files
    dirs += [
        Path("core/src/main/resources/textures"),
        Path("core/src/main/resources/sounds"),
        Path("core/src/main/resources/ui"),
        Path("core/src/main/resources/maps"),
        Path("core/src/main/resources/weapons"),
    ]

    # ---------------- android ----------------
    android_base = Path("android/src/main/kotlin") / Path(*android_pkg.split("."))
    files.append(FileSpec(
        str(android_base / "AndroidLauncher.kt"),
        f"""package {android_pkg}

import android.os.Bundle
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import {client_pkg}.GameApp

class AndroidLauncher : AndroidApplication() {{
    override fun onCreate(savedInstanceState: Bundle?) {{
        super.onCreate(savedInstanceState)
        val config = AndroidApplicationConfiguration()
        initialize(GameApp(), config)
    }}
}}
"""
    ))
    files.append(FileSpec(
        str(android_base / "AndroidPlatform.kt"),
        kotlin_object_placeholder(android_pkg, "AndroidPlatform")
    ))

    # ---------------- server ----------------
    server_base = Path("server/src/main/kotlin") / Path(*server_pkg.split("."))

    files.append(FileSpec(
        str(server_base / "ServerMain.kt"),
        f"""package {server_pkg}

fun main() {{
    // TODO: start WebSocket server and tick loop
    println("Server starting...")
}}
"""
    ))

    # server config
    files += [
        FileSpec(str(server_base / "config/ServerConfig.kt"),
                 f"""package {server_pkg}.config

data class ServerConfig(
    val port: Int = 8080,
    val tickHz: Int = 20,
    val maxPlayers: Int = 8
)
"""),
        FileSpec(str(server_base / "config/LoggingConfig.kt"),
                 kotlin_object_placeholder(f"{server_pkg}.config", "LoggingConfig")),
    ]

    # server presentation
    files += [
        FileSpec(str(server_base / "presentation/websocket/WebSocketServer.kt"),
                 kotlin_placeholder(f"{server_pkg}.presentation.websocket", "WebSocketServer")),
        FileSpec(str(server_base / "presentation/websocket/SessionHandler.kt"),
                 kotlin_placeholder(f"{server_pkg}.presentation.websocket", "SessionHandler")),
        FileSpec(str(server_base / "presentation/websocket/MessageRouter.kt"),
                 kotlin_placeholder(f"{server_pkg}.presentation.websocket", "MessageRouter")),
        FileSpec(str(server_base / "presentation/health/HealthEndpoint.kt"),
                 kotlin_placeholder(f"{server_pkg}.presentation.health", "HealthEndpoint")),
    ]

    # server application
    for u in ["HandleJoinUseCase", "HandleLeaveUseCase", "HandleInputUseCase", "BroadcastSnapshotUseCase"]:
        files.append(FileSpec(
            str(server_base / f"application/usecase/{u}.kt"),
            kotlin_placeholder(f"{server_pkg}.application.usecase", u)
        ))
    for s in ["MatchService", "TickService"]:
        files.append(FileSpec(
            str(server_base / f"application/service/{s}.kt"),
            kotlin_placeholder(f"{server_pkg}.application.service", s)
        ))

    # server domain
    for m in ["ServerGameState", "PlayerState", "ProjectileState", "PickupState", "Tile"]:
        files.append(FileSpec(
            str(server_base / f"domain/model/{m}.kt"),
            kotlin_placeholder(f"{server_pkg}.domain.model", m)
        ))
    for s in ["ServerWorldStepSystem", "CollisionSystem", "ProjectileSystem", "PickupSpawnSystem"]:
        files.append(FileSpec(
            str(server_base / f"domain/system/{s}.kt"),
            kotlin_placeholder(f"{server_pkg}.domain.system", s)
        ))
    files += [
        FileSpec(str(server_base / "domain/ports/MapProviderPort.kt"),
                 kotlin_interface_placeholder(f"{server_pkg}.domain.ports", "MapProviderPort")),
        FileSpec(str(server_base / "domain/ports/ClockPort.kt"),
                 kotlin_interface_placeholder(f"{server_pkg}.domain.ports", "ClockPort")),
    ]

    # server data
    files += [
        FileSpec(str(server_base / "data/map/MapProvider.kt"),
                 kotlin_placeholder(f"{server_pkg}.data.map", "MapProvider")),
        FileSpec(str(server_base / "data/map/MapParser.kt"),
                 kotlin_object_placeholder(f"{server_pkg}.data.map", "MapParser")),
        FileSpec(str(server_base / "data/persistence/MatchLogRepository.kt"),
                 kotlin_placeholder(f"{server_pkg}.data.persistence", "MatchLogRepository")),
    ]

    # server util
    files += [
        FileSpec(str(server_base / "util/IdGenerator.kt"),
                 kotlin_object_placeholder(f"{server_pkg}.util", "IdGenerator",
                 "    fun newId(): String = java.util.UUID.randomUUID().toString()\n")),
        FileSpec(str(server_base / "util/RateLimiter.kt"),
                 kotlin_placeholder(f"{server_pkg}.util", "RateLimiter")),
    ]

    # server resources
    files += [
        FileSpec("server/src/main/resources/maps/map01.json", "{\n  \"width\": 10,\n  \"height\": 10,\n  \"tiles\": []\n}\n"),
        FileSpec("server/src/main/resources/server.conf", "port=8080\ntickHz=20\nmaxPlayers=8\n"),
    ]
    dirs += [
        Path("server/src/main/resources/maps"),
    ]

    return dirs, files

# ---------- main ----------

def main() -> int:
    project_name = sys.argv[1] if len(sys.argv) > 1 else "MyTopDownShooter"
    root = Path(project_name).resolve()

    dirs, files = build_structure(project_name)

    ensure_dir(root)

    # Create directories
    for d in dirs:
        ensure_dir(root / d)

    # Create files
    for f in files:
        write_file(root / f.relpath, f.content)

    print(f"Done. Created skeleton at: {root}")
    if not OVERWRITE:
        print("Note: Existing files were not overwritten. Set OVERWRITE=True to overwrite.")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())