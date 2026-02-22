package com.mygame.client;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector3;
import com.mygame.client.net.NetClient;
import com.mygame.client.net.NetListener;
import com.mygame.shared.dto.GameSnapshotDto;
import com.mygame.shared.dto.MapDto;
import com.mygame.shared.dto.PlayerDto;
import com.mygame.shared.dto.TileType;
import com.mygame.shared.protocol.messages.InputMessage;
import com.mygame.shared.util.Vec2;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class GameClientApp extends ApplicationAdapter implements NetListener {

    // Networking
    private NetClient net;
    private final AtomicReference<MapDto> mapRef = new AtomicReference<>();
    private final AtomicReference<GameSnapshotDto> snapRef = new AtomicReference<>();
    private volatile String localPlayerId;

    // Rendering
    private OrthographicCamera camera;
    private ShapeRenderer shapes;

    // Input send pacing
    private final AtomicInteger seq = new AtomicInteger(1);
    private float inputSendAccumulator = 0f;
    private static final float INPUT_SEND_HZ = 20f;
    private static final float INPUT_SEND_DT = 1f / INPUT_SEND_HZ;

    // Config
    private final String serverUrl;
    private final String username;

    public GameClientApp(String serverUrl, String username) {
        this.serverUrl = serverUrl;
        this.username = username;
    }

    @Override
    public void create() {
        camera = new OrthographicCamera();
        camera.setToOrtho(false, 32f, 18f); // world units visible; tweak later
        camera.update();

        shapes = new ShapeRenderer();

        // Connect
        net = new NetClient(serverUrl, username, this);
        net.connectAsync();
    }

    @Override
    public void render() {
        // Update input → send to server
        float dt = Gdx.graphics.getDeltaTime();
        inputSendAccumulator += dt;
        while (inputSendAccumulator >= INPUT_SEND_DT) {
            inputSendAccumulator -= INPUT_SEND_DT;
            sendInput();
        }

        // Render
        Gdx.gl.glClearColor(0.08f, 0.08f, 0.10f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        camera.update();
        shapes.setProjectionMatrix(camera.combined);

        MapDto map = mapRef.get();
        GameSnapshotDto snap = snapRef.get();

        shapes.begin(ShapeRenderer.ShapeType.Filled);

        if (map != null) drawMap(map);
        if (snap != null) drawPlayers(snap);

        shapes.end();
    }

    private void sendInput() {
        if (net == null || !net.isOpen()) return;

        // MVP desktop controls:
        // - Move: WASD
        // - Aim: mouse position in world
        // - Shoot: left mouse button
        // - Switch weapon: SPACE
        float mx = 0f, my = 0f;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) mx -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.D)) mx += 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.S)) my -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.W)) my += 1f;

        // Aim vector: from player pos → mouse world
        Vec2 aim = computeAimVector();

        boolean shoot = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
        boolean switchWeapon = Gdx.input.isKeyJustPressed(Input.Keys.SPACE);

        InputMessage msg = new InputMessage(
                seq.getAndIncrement(),
                new Vec2(mx, my),
                aim,
                shoot,
                switchWeapon
        );
        net.sendInput(msg);
    }

    private Vec2 computeAimVector() {
        GameSnapshotDto snap = snapRef.get();
        if (snap == null || localPlayerId == null) return new Vec2(1f, 0f);

        PlayerDto me = null;
        for (PlayerDto p : snap.players) {
            if (p.playerId != null && p.playerId.equals(localPlayerId)) {
                me = p; break;
            }
        }
        if (me == null || me.pos == null) return new Vec2(1f, 0f);

        // Screen → world
        Vector3 mouseWorld = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0f);
        camera.unproject(mouseWorld);
        float ax = mouseWorld.x - me.pos.x;
        float ay = mouseWorld.y - me.pos.y;
        float len2 = ax * ax + ay * ay;
        if (len2 < 1e-6f) return new Vec2(0f, 0f);
        float inv = (float)(1.0 / Math.sqrt(len2));
        return new Vec2(ax * inv, ay * inv);
    }

    private void drawMap(MapDto map) {
        // Each tile is 1x1 world unit
        for (int y = 0; y < map.height; y++) {
            for (int x = 0; x < map.width; x++) {
                TileType t = map.tiles[y * map.width + x];
                setTileColor(t);
                shapes.rect(x, y, 1f, 1f);
            }
        }
    }

    private void setTileColor(TileType t) {
        // Simple MVP palette
        switch (t) {
            case WALL:
                shapes.setColor(0.20f, 0.20f, 0.22f, 1f);
                break;
            case WINDOW:
                shapes.setColor(0.25f, 0.35f, 0.45f, 1f);
                break;
            case TRAP:
                shapes.setColor(0.50f, 0.20f, 0.20f, 1f);
                break;
            case FLOOR:
            default:
                shapes.setColor(0.12f, 0.12f, 0.14f, 1f);
                break;
        }
    }

    private void drawPlayers(GameSnapshotDto snap) {
        for (PlayerDto p : snap.players) {
            if (p.pos == null) continue;

            boolean isMe = localPlayerId != null && localPlayerId.equals(p.playerId);

            if (isMe) shapes.setColor(0.20f, 0.85f, 0.20f, 1f);
            else shapes.setColor(0.85f, 0.20f, 0.20f, 1f);

            // body
            shapes.circle(p.pos.x, p.pos.y, 0.30f, 16);

            // facing line
            if (p.facing != null) {
                shapes.setColor(0.95f, 0.95f, 0.95f, 1f);
                shapes.rectLine(
                        p.pos.x, p.pos.y,
                        p.pos.x + p.facing.x * 0.6f,
                        p.pos.y + p.facing.y * 0.6f,
                        0.06f
                );
            }
        }
    }

    @Override
    public void dispose() {
        if (net != null) net.close();
        if (shapes != null) shapes.dispose();
    }

    // ---- NetListener ----

    @Override
    public void onJoinAccepted(String playerId, MapDto map, GameSnapshotDto initialSnapshot) {
        this.localPlayerId = playerId;
        this.mapRef.set(map);
        this.snapRef.set(initialSnapshot);
        System.out.println("[CLIENT] JoinAccepted playerId=" + playerId + " map=" + (map != null ? map.mapId : "(null)"));
    }

    @Override
    public void onSnapshot(GameSnapshotDto snapshot) {
        this.snapRef.set(snapshot);
    }

    @Override
    public void onError(String code, String message) {
        System.err.println("[CLIENT] Server error: " + code + " " + message);
    }

    @Override
    public void onDisconnected(String reason) {
        System.err.println("[CLIENT] Disconnected: " + reason);
    }
}