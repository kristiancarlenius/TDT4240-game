package com.mygame.client.presentation.screens;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.Screen;
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
import com.mygame.client.presentation.navigation.Navigator;
import com.mygame.client.presentation.view.input.VirtualJoystickView;
import com.mygame.shared.dto.*;
import com.mygame.shared.protocol.messages.InputMessage;
import com.mygame.shared.util.Vec2;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class GameScreen implements Screen, NetListener {

    private static final float INPUT_SEND_HZ = 20f;
    private static final float INPUT_SEND_DT = 1f / INPUT_SEND_HZ;

    private final Navigator navigator;
    private final String    serverUrl;
    private final String    username;

    // Network
    private NetClient net;
    private final AtomicReference<MapDto>          mapRef  = new AtomicReference<>();
    private final AtomicReference<GameSnapshotDto> snapRef = new AtomicReference<>();
    private volatile String localPlayerId;

    // Input pacing
    private final AtomicInteger seq              = new AtomicInteger(1);
    private       float         inputAccumulator = 0f;

    // Rendering resources (created in show())
    private OrthographicCamera camera;
    private ShapeRenderer      shapes;
    private SpriteBatch        batch;
    private BitmapFont         font;
    private BitmapFont         bigFont;
    private final GlyphLayout  layout = new GlyphLayout();
    private Matrix4            screenProj;

    // Touch controls (Android only, created in show() when platform == Android)
    private VirtualJoystickView moveStick;
    private VirtualJoystickView aimStick;
    // Weapon-switch button bounds (screen pixels, bottom-left origin)
    private float   switchBtnX, switchBtnY, switchBtnR;
    // Set true when the switch button is tapped; consumed in sendInput()
    private boolean touchSwitchPending = false;

    public GameScreen(Navigator navigator, String serverUrl, String username) {
        this.navigator = navigator;
        this.serverUrl = serverUrl;
        this.username  = username;
    }

    // ---- Screen lifecycle ----

    @Override
    public void show() {
        camera = new OrthographicCamera();
        camera.setToOrtho(false, 32f, 18f);
        camera.update();

        shapes  = new ShapeRenderer();
        batch   = new SpriteBatch();
        font    = new BitmapFont();
        font.getData().setScale(1.4f);
        bigFont = new BitmapFont();
        bigFont.getData().setScale(3.5f);
        screenProj = new Matrix4();
        rebuildScreenProj();

        if (Gdx.app.getType() == Application.ApplicationType.Android) {
            setupTouchControls();
        }

        net = new NetClient(serverUrl, username, this);
        net.connectAsync();
    }

    private void setupTouchControls() {
        int sw = Gdx.graphics.getWidth();
        int sh = Gdx.graphics.getHeight();

        // Left stick: lower-left quadrant
        moveStick = new VirtualJoystickView(sw * 0.18f, sh * 0.22f);
        // Right stick: lower-right quadrant
        aimStick  = new VirtualJoystickView(sw * 0.82f, sh * 0.22f);
        // Weapon-switch button: above right stick
        switchBtnX = sw * 0.82f;
        switchBtnY = sh * 0.42f;
        switchBtnR = 40f;

        Gdx.input.setInputProcessor(new InputAdapter() {
            private int switchPointer = -1;

            @Override
            public boolean touchDown(int sx, int sy, int pointer, int button) {
                int sh = Gdx.graphics.getHeight();
                float bx = sx - switchBtnX;
                float by = (sh - sy) - switchBtnY;
                if (bx * bx + by * by <= switchBtnR * switchBtnR && switchPointer == -1) {
                    switchPointer      = pointer;
                    touchSwitchPending = true;
                    return true;
                }
                if (moveStick.touchDown(sx, sy, pointer, sh)) return true;
                if (aimStick.touchDown(sx, sy, pointer, sh))  return true;
                return false;
            }

            @Override
            public boolean touchDragged(int sx, int sy, int pointer) {
                int sh = Gdx.graphics.getHeight();
                if (moveStick.touchDragged(sx, sy, pointer, sh)) return true;
                if (aimStick.touchDragged(sx, sy, pointer, sh))  return true;
                return false;
            }

            @Override
            public boolean touchUp(int sx, int sy, int pointer, int button) {
                if (pointer == switchPointer) { switchPointer = -1; return true; }
                int sh = Gdx.graphics.getHeight();
                if (moveStick.touchUp(sx, sy, pointer)) return true;
                if (aimStick.touchUp(sx, sy, pointer))  return true;
                return false;
            }
        });
    }

    @Override
    public void render(float delta) {
        // ESC → back to main menu
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            navigator.showMainMenu(serverUrl, username);
            return;
        }

        GameSnapshotDto snap = snapRef.get();
        MapDto          map  = mapRef.get();
        PlayerDto       me   = findLocalPlayer(snap);

        updateCamera(me, map);

        // Send input at fixed rate (skip while dead)
        inputAccumulator += delta;
        while (inputAccumulator >= INPUT_SEND_DT) {
            inputAccumulator -= INPUT_SEND_DT;
            if (me == null || !me.isDead) sendInput();
        }

        Gdx.gl.glClearColor(0.08f, 0.08f, 0.10f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // World pass
        camera.update();
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        if (map  != null) drawMap(map);
        if (snap != null) {
            drawPickups(snap);
            drawPlayers(snap);
            drawProjectiles(snap);
        }
        shapes.end();

        // HUD pass
        if (snap != null && me != null) {
            drawHud(me, snap);
        } else {
            drawConnecting();
        }
    }

    @Override
    public void resize(int width, int height) {
        rebuildScreenProj();
    }

    @Override public void pause()  {}
    @Override public void resume() {}

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
    }

    @Override
    public void dispose() {
        if (net    != null) net.close();
        if (shapes != null) shapes.dispose();
        if (batch  != null) batch.dispose();
        if (font   != null) font.dispose();
        if (bigFont!= null) bigFont.dispose();
    }

    // ---- Camera ----

    private void updateCamera(PlayerDto me, MapDto map) {
        if (me == null || me.pos == null || map == null) return;
        float camX = map.width / 2f;
        float halfH = 9f;
        float camY = Math.max(halfH, Math.min(map.height - halfH, me.pos.y));
        camera.position.set(camX, camY, 0);
    }

    // ---- World rendering ----

    private void drawMap(MapDto map) {
        for (int y = 0; y < map.height; y++) {
            for (int x = 0; x < map.width; x++) {
                setTileColor(map.tiles[y * map.width + x]);
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

    private void drawPickups(GameSnapshotDto snap) {
        if (snap.pickups == null) return;
        for (PickupDto p : snap.pickups) {
            if (p == null || p.pos == null) continue;
            shapes.setColor(0.10f, 0.10f, 0.10f, 1f);
            shapes.circle(p.pos.x, p.pos.y, 0.38f, 16);
            switch (p.type) {
                case HEALTH: shapes.setColor(0.20f, 0.85f, 0.35f, 1f); break;
                case SPEED:  shapes.setColor(0.20f, 0.80f, 0.95f, 1f); break;
                case WEAPON: shapes.setColor(0.95f, 0.55f, 0.10f, 1f); break;
                default:     shapes.setColor(0.80f, 0.80f, 0.80f, 1f); break;
            }
            shapes.circle(p.pos.x, p.pos.y, 0.28f, 16);
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
                shapes.rectLine(p.pos.x, p.pos.y,
                        p.pos.x + p.facing.x * 0.6f,
                        p.pos.y + p.facing.y * 0.6f, 0.06f);
            }
        }
    }

    private void drawProjectiles(GameSnapshotDto snap) {
        if (snap.projectiles == null) return;
        shapes.setColor(0.95f, 0.90f, 0.20f, 1f);
        for (ProjectileDto pr : snap.projectiles) {
            if (pr == null || pr.pos == null) continue;
            float r = pr.radius > 0 ? pr.radius : 0.10f;
            shapes.circle(pr.pos.x, pr.pos.y, r, 12);
        }
    }

    // ---- HUD ----

    private void drawHud(PlayerDto me, GameSnapshotDto snap) {
        if (me.isDead) {
            drawDeathOverlay(me);
            if (moveStick != null) drawTouchControls();
            return;
        }

        int sw = Gdx.graphics.getWidth();
        int sh = Gdx.graphics.getHeight();

        float hpFrac = Math.max(0f, Math.min(1f, me.hp / 100f));
        int barX = 20, barY = 20, barW = 200, barH = 18;

        // Health bar (screen-space shapes)
        shapes.setProjectionMatrix(screenProj);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.25f, 0.25f, 0.25f, 1f);
        shapes.rect(barX, barY, barW, barH);
        shapes.setColor(
                hpFrac < 0.5f ? 0.9f : (2f - 2f * hpFrac) * 0.9f,
                hpFrac > 0.5f ? 0.85f : (2f * hpFrac) * 0.85f,
                0.10f, 1f);
        shapes.rect(barX, barY, barW * hpFrac, barH);
        shapes.end();

        batch.setProjectionMatrix(screenProj);
        batch.begin();

        font.setColor(Color.WHITE);
        font.draw(batch, "HP  " + (int) me.hp + " / 100", barX, barY + barH + 18f);

        String weaponLine = me.equippedWeaponType != null
                ? me.equippedWeaponType.name() + "   " + me.equippedAmmo + " ammo" : "—";
        font.draw(batch, weaponLine, barX, barY + barH + 36f);

        if (me.speedBoostTimer > 0f) {
            font.setColor(new Color(0.20f, 0.85f, 0.95f, 1f));
            font.draw(batch, "SPEED BOOST  " + (int) Math.ceil(me.speedBoostTimer) + "s",
                    barX, barY + barH + 56f);
        }

        // Score (top-right)
        font.setColor(new Color(1f, 0.85f, 0.2f, 1f));
        String scoreText = "Kills: " + me.score;
        layout.setText(font, scoreText);
        font.draw(batch, scoreText, sw - layout.width - 20f, sh - 20f);

        // Leaderboard
        font.setColor(Color.LIGHT_GRAY);
        float lbY = sh - 50f;
        for (PlayerDto p : snap.players) {
            if (localPlayerId.equals(p.playerId)) continue;
            font.draw(batch, p.username + ": " + p.score + " kills", sw - 200f, lbY);
            lbY -= 22f;
        }

        batch.end();

        if (moveStick != null) {
            drawTouchControls();
        }
    }

    private void drawTouchControls() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapes.setProjectionMatrix(screenProj);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        moveStick.render(shapes);
        aimStick.render(shapes);

        // Weapon-switch button
        shapes.setColor(0.55f, 0.30f, 0.80f, 0.75f);
        shapes.circle(switchBtnX, switchBtnY, switchBtnR, 24);
        shapes.setColor(0.35f, 0.15f, 0.55f, 0.85f);
        shapes.circle(switchBtnX, switchBtnY, switchBtnR - 5f, 24);

        shapes.end();

        // "SW" label on switch button
        batch.setProjectionMatrix(screenProj);
        batch.begin();
        font.setColor(Color.WHITE);
        layout.setText(font, "SW");
        font.draw(batch, "SW",
                switchBtnX - layout.width / 2f,
                switchBtnY + layout.height / 2f);
        batch.end();
    }

    private void drawDeathOverlay(PlayerDto me) {
        int sw = Gdx.graphics.getWidth();
        int sh = Gdx.graphics.getHeight();

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapes.setProjectionMatrix(screenProj);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.55f);
        shapes.rect(0, 0, sw, sh);
        shapes.end();

        batch.setProjectionMatrix(screenProj);
        batch.begin();

        bigFont.setColor(new Color(0.9f, 0.2f, 0.2f, 1f));
        layout.setText(bigFont, "YOU DIED");
        bigFont.draw(batch, "YOU DIED", (sw - layout.width) / 2f, sh / 2f + 60f);

        font.setColor(Color.WHITE);
        String respawn = "Respawning in " + Math.max(1, (int) Math.ceil(me.respawnTimer)) + "...";
        layout.setText(font, respawn);
        font.draw(batch, respawn, (sw - layout.width) / 2f, sh / 2f - 10f);

        batch.end();
    }

    private void drawConnecting() {
        int sw = Gdx.graphics.getWidth();
        int sh = Gdx.graphics.getHeight();
        batch.setProjectionMatrix(screenProj);
        batch.begin();
        font.setColor(Color.LIGHT_GRAY);
        layout.setText(font, "Connecting...");
        font.draw(batch, "Connecting...", (sw - layout.width) / 2f, sh / 2f);
        batch.end();
    }

    // ---- Input ----

    private void sendInput() {
        if (net == null || !net.isOpen()) return;

        Vec2    move;
        Vec2    aim;
        boolean shoot;
        boolean switchWp;

        if (moveStick != null) {
            // Android touch controls
            move     = moveStick.getDirection();
            Vec2 aimDir = aimStick.getDirection();
            // If aim stick is idle, keep last facing (send zero → server ignores)
            aim      = aimDir;
            shoot    = aimStick.isActive();  // auto-shoot when aim stick is held
            switchWp = touchSwitchPending;
            touchSwitchPending = false;
        } else {
            // Desktop keyboard + mouse
            float mx = 0f, my = 0f;
            if (Gdx.input.isKeyPressed(Input.Keys.A)) mx -= 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.D)) mx += 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.S)) my -= 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.W)) my += 1f;
            move     = new Vec2(mx, my);
            aim      = computeAimVector();
            shoot    = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
            switchWp = Gdx.input.isKeyJustPressed(Input.Keys.SPACE);
        }

        net.sendInput(new InputMessage(seq.getAndIncrement(), move, aim, shoot, switchWp));
    }

    private Vec2 computeAimVector() {
        GameSnapshotDto snap = snapRef.get();
        PlayerDto me = findLocalPlayer(snap);
        if (me == null || me.pos == null) return new Vec2(1f, 0f);
        Vector3 mw = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0f);
        camera.unproject(mw);
        float ax = mw.x - me.pos.x, ay = mw.y - me.pos.y;
        float len2 = ax * ax + ay * ay;
        if (len2 < 1e-6f) return new Vec2(0f, 0f);
        float inv = (float) (1.0 / Math.sqrt(len2));
        return new Vec2(ax * inv, ay * inv);
    }

    // ---- Helpers ----

    private PlayerDto findLocalPlayer(GameSnapshotDto snap) {
        if (snap == null || localPlayerId == null) return null;
        for (PlayerDto p : snap.players) {
            if (localPlayerId.equals(p.playerId)) return p;
        }
        return null;
    }

    private void rebuildScreenProj() {
        if (screenProj == null) screenProj = new Matrix4();
        screenProj.setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    // ---- NetListener ----

    @Override
    public void onJoinAccepted(String playerId, MapDto map, GameSnapshotDto initialSnapshot) {
        this.localPlayerId = playerId;
        mapRef.set(map);
        snapRef.set(initialSnapshot);
        System.out.println("[GAME] Joined as " + playerId);
    }

    @Override
    public void onSnapshot(GameSnapshotDto snapshot) {
        snapRef.set(snapshot);
    }

    @Override
    public void onError(String code, String message) {
        System.err.println("[GAME] Server error: " + code + " – " + message);
    }

    @Override
    public void onDisconnected(String reason) {
        System.err.println("[GAME] Disconnected: " + reason);
        // Must transition on the GL thread
        Gdx.app.postRunnable(() -> navigator.showMainMenu(serverUrl, username));
    }
}