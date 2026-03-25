package com.mygame.client;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.mygame.client.net.NetClient;
import com.mygame.client.net.NetListener;
import com.mygame.shared.dto.GameSnapshotDto;
import com.mygame.shared.dto.MapDto;
import com.mygame.shared.dto.PickupDto;
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
    private SpriteBatch batch;
    private BitmapFont font;
    private BitmapFont bigFont;
    private final GlyphLayout glyphLayout = new GlyphLayout();

    // Cached screen projection matrix for HUD (rebuilt on resize)
    private Matrix4 screenProjection = new Matrix4();

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
        camera.setToOrtho(false, 32f, 18f);
        camera.update();

        shapes = new ShapeRenderer();
        batch = new SpriteBatch();

        font = new BitmapFont();
        font.setColor(Color.WHITE);
        font.getData().setScale(1.4f);

        bigFont = new BitmapFont();
        bigFont.getData().setScale(3.5f);

        rebuildScreenProjection();

        net = new NetClient(serverUrl, username, this);
        net.connectAsync();
    }

    @Override
    public void resize(int width, int height) {
        rebuildScreenProjection();
    }

    private void rebuildScreenProjection() {
        screenProjection.setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    // ---- Rendering ----

    @Override
    public void render() {
        float dt = Gdx.graphics.getDeltaTime();

        GameSnapshotDto snap = snapRef.get();
        MapDto map = mapRef.get();

        // Find local player data
        PlayerDto localPlayer = findLocalPlayer(snap);

        // Update camera to track local player
        updateCamera(localPlayer, map);

        // Send input at fixed rate (skip if dead)
        inputSendAccumulator += dt;
        while (inputSendAccumulator >= INPUT_SEND_DT) {
            inputSendAccumulator -= INPUT_SEND_DT;
            if (localPlayer == null || !localPlayer.isDead) {
                sendInput();
            }
        }

        // Clear
        Gdx.gl.glClearColor(0.08f, 0.08f, 0.10f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // --- World rendering (camera projection) ---
        camera.update();
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        if (map != null) drawMap(map);
        if (snap != null) {
            drawPickups(snap);
            drawPlayers(snap);
            drawProjectiles(snap);
        }
        shapes.end();

        // --- HUD rendering (screen projection) ---
        if (snap != null && localPlayer != null) {
            drawHud(localPlayer, snap);
        } else if (localPlayer == null && localPlayerId == null) {
            drawConnecting();
        }
    }

    private void updateCamera(PlayerDto localPlayer, MapDto map) {
        if (localPlayer == null || localPlayer.pos == null || map == null) return;

        // Map fits horizontally (30 < 32 viewport), so fix X at center
        float camX = map.width / 2f;

        // Clamp Y so we don't show outside map
        float halfH = 9f;
        float camY = Math.max(halfH, Math.min(map.height - halfH, localPlayer.pos.y));

        camera.position.set(camX, camY, 0);
    }

    private PlayerDto findLocalPlayer(GameSnapshotDto snap) {
        if (snap == null || localPlayerId == null) return null;
        for (PlayerDto p : snap.players) {
            if (localPlayerId.equals(p.playerId)) return p;
        }
        return null;
    }

    // ---- World draw methods ----

    private void drawMap(MapDto map) {
        for (int y = 0; y < map.height; y++) {
            for (int x = 0; x < map.width; x++) {
                TileType t = map.tiles[y * map.width + x];
                setTileColor(t);
                shapes.rect(x, y, 1f, 1f);
            }
        }
    }

    private void setTileColor(TileType t) {
        switch (t) {
            case WALL:   shapes.setColor(0.20f, 0.20f, 0.22f, 1f); break;
            case WINDOW: shapes.setColor(0.25f, 0.35f, 0.45f, 1f); break;
            case TRAP:   shapes.setColor(0.50f, 0.20f, 0.20f, 1f); break;
            default:     shapes.setColor(0.12f, 0.12f, 0.14f, 1f); break;
        }
    }

    private void drawPlayers(GameSnapshotDto snap) {
        for (PlayerDto p : snap.players) {
            if (p.pos == null || p.isDead) continue;

            boolean isMe = localPlayerId != null && localPlayerId.equals(p.playerId);
            shapes.setColor(isMe ? new Color(0.20f, 0.85f, 0.20f, 1f)
                                 : new Color(0.85f, 0.20f, 0.20f, 1f));
            shapes.circle(p.pos.x, p.pos.y, 0.30f, 16);

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

    private void drawProjectiles(GameSnapshotDto snap) {
        if (snap.projectiles == null) return;
        shapes.setColor(0.95f, 0.90f, 0.20f, 1f);
        for (var pr : snap.projectiles) {
            if (pr == null || pr.pos == null) continue;
            float r = pr.radius > 0 ? pr.radius : 0.10f;
            shapes.circle(pr.pos.x, pr.pos.y, r, 12);
        }
    }

    private void drawPickups(GameSnapshotDto snap) {
        if (snap.pickups == null) return;
        for (PickupDto pickup : snap.pickups) {
            if (pickup == null || pickup.pos == null) continue;

            float x = pickup.pos.x;
            float y = pickup.pos.y;

            // Outer ring (dark background)
            shapes.setColor(0.10f, 0.10f, 0.10f, 1f);
            shapes.circle(x, y, 0.38f, 16);

            // Coloured fill by type
            switch (pickup.type) {
                case HEALTH: shapes.setColor(0.20f, 0.85f, 0.35f, 1f); break; // green
                case SPEED:  shapes.setColor(0.20f, 0.80f, 0.95f, 1f); break; // cyan
                case WEAPON: shapes.setColor(0.95f, 0.55f, 0.10f, 1f); break; // orange
                default:     shapes.setColor(0.80f, 0.80f, 0.80f, 1f); break;
            }
            shapes.circle(x, y, 0.28f, 16);
        }
    }

    // ---- HUD ----

    private void drawHud(PlayerDto local, GameSnapshotDto snap) {
        int sw = Gdx.graphics.getWidth();
        int sh = Gdx.graphics.getHeight();

        if (local.isDead) {
            drawDeathOverlay(local, sw, sh);
            return;
        }

        // Health bar (bottom-left)
        float hpFrac = Math.max(0f, Math.min(1f, local.hp / 100f));
        int barX = 20, barY = 20, barW = 200, barH = 18;

        shapes.setProjectionMatrix(screenProjection);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.25f, 0.25f, 0.25f, 1f);
        shapes.rect(barX, barY, barW, barH);
        // Colour: green → yellow → red based on HP
        shapes.setColor(hpFrac < 0.5f ? 0.9f : (2f - 2f * hpFrac) * 0.9f,
                        hpFrac > 0.5f ? 0.85f : (2f * hpFrac) * 0.85f,
                        0.10f, 1f);
        shapes.rect(barX, barY, barW * hpFrac, barH);
        shapes.end();

        batch.setProjectionMatrix(screenProjection);
        batch.begin();

        // HP label
        font.setColor(Color.WHITE);
        font.draw(batch, "HP  " + (int) local.hp + " / 100", barX, barY + barH + 18);

        // Weapon + ammo
        String weaponLine = local.equippedWeaponType != null
                ? local.equippedWeaponType.name() + "   " + local.equippedAmmo + " ammo"
                : "—";
        font.draw(batch, weaponLine, barX, barY + barH + 36);

        // Speed boost indicator
        if (local.speedBoostTimer > 0f) {
            font.setColor(new Color(0.20f, 0.85f, 0.95f, 1f));
            font.draw(batch, "SPEED BOOST  " + (int) Math.ceil(local.speedBoostTimer) + "s",
                    barX, barY + barH + 56);
        }

        // Score (top-right)
        font.setColor(new Color(1f, 0.85f, 0.2f, 1f));
        String scoreText = "Kills: " + local.score;
        glyphLayout.setText(font, scoreText);
        font.draw(batch, scoreText, sw - glyphLayout.width - 20, sh - 20);

        // Other players' scores (leaderboard top-right, below kills)
        font.setColor(Color.LIGHT_GRAY);
        int lbY = sh - 50;
        for (PlayerDto p : snap.players) {
            if (localPlayerId.equals(p.playerId)) continue;
            font.draw(batch, p.username + ": " + p.score + " kills", sw - 200, lbY);
            lbY -= 22;
        }

        batch.end();
    }

    private void drawDeathOverlay(PlayerDto local, int sw, int sh) {
        // Dark overlay
        shapes.setProjectionMatrix(screenProjection);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.55f);
        shapes.rect(0, 0, sw, sh);
        shapes.end();

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        batch.setProjectionMatrix(screenProjection);
        batch.begin();

        bigFont.setColor(new Color(0.9f, 0.2f, 0.2f, 1f));
        String dead = "YOU DIED";
        glyphLayout.setText(bigFont, dead);
        bigFont.draw(batch, dead, (sw - glyphLayout.width) / 2f, sh / 2f + 60);

        int secs = Math.max(1, (int) Math.ceil(local.respawnTimer));
        font.setColor(Color.WHITE);
        String respawn = "Respawning in " + secs + "...";
        glyphLayout.setText(font, respawn);
        font.draw(batch, respawn, (sw - glyphLayout.width) / 2f, sh / 2f - 10);

        batch.end();
    }

    private void drawConnecting() {
        int sw = Gdx.graphics.getWidth();
        int sh = Gdx.graphics.getHeight();
        batch.setProjectionMatrix(screenProjection);
        batch.begin();
        font.setColor(Color.LIGHT_GRAY);
        String msg = "Connecting...";
        glyphLayout.setText(font, msg);
        font.draw(batch, msg, (sw - glyphLayout.width) / 2f, sh / 2f);
        batch.end();
    }

    // ---- Input ----

    private void sendInput() {
        if (net == null || !net.isOpen()) return;

        float mx = 0f, my = 0f;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) mx -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.D)) mx += 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.S)) my -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.W)) my += 1f;

        Vec2 aim = computeAimVector();
        boolean shoot = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
        boolean switchWeapon = Gdx.input.isKeyJustPressed(Input.Keys.SPACE);

        net.sendInput(new InputMessage(seq.getAndIncrement(), new Vec2(mx, my), aim, shoot, switchWeapon));
    }

    private Vec2 computeAimVector() {
        GameSnapshotDto snap = snapRef.get();
        if (snap == null || localPlayerId == null) return new Vec2(1f, 0f);

        PlayerDto me = findLocalPlayer(snap);
        if (me == null || me.pos == null) return new Vec2(1f, 0f);

        Vector3 mouseWorld = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0f);
        camera.unproject(mouseWorld);
        float ax = mouseWorld.x - me.pos.x;
        float ay = mouseWorld.y - me.pos.y;
        float len2 = ax * ax + ay * ay;
        if (len2 < 1e-6f) return new Vec2(0f, 0f);
        float inv = (float) (1.0 / Math.sqrt(len2));
        return new Vec2(ax * inv, ay * inv);
    }

    // ---- Lifecycle ----

    @Override
    public void dispose() {
        if (net != null) net.close();
        if (shapes != null) shapes.dispose();
        if (batch != null) batch.dispose();
        if (font != null) font.dispose();
        if (bigFont != null) bigFont.dispose();
    }

    // ---- NetListener ----

    @Override
    public void onJoinAccepted(String playerId, MapDto map, GameSnapshotDto initialSnapshot) {
        this.localPlayerId = playerId;
        this.mapRef.set(map);
        this.snapRef.set(initialSnapshot);
        System.out.println("[CLIENT] JoinAccepted playerId=" + playerId);
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
