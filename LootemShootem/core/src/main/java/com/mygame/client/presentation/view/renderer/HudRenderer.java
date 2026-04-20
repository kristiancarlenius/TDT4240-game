package com.mygame.client.presentation.view.renderer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.mygame.client.domain.model.HudSlot;
import com.mygame.client.domain.model.HudWidget;
import com.mygame.client.domain.model.WorldState;
import com.mygame.client.domain.ports.PreferencesPort;
import com.mygame.client.presentation.view.input.InputHandler;
import com.mygame.shared.dto.ChestDto;
import com.mygame.shared.dto.GameSnapshotDto;
import com.mygame.shared.dto.MapDto;
import com.mygame.shared.dto.PlayerDto;
import com.mygame.shared.dto.TileType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class HudRenderer {

    private static final int LEADERBOARD_ROWS = 9;

    private final WorldState      worldState;
    private final InputHandler    inputHandler;
    private final PreferencesPort prefs;
    private final ShapeRenderer   shapes;
    private final SpriteBatch     batch;
    private final BitmapFont      font;
    private final BitmapFont      bigFont;
    private final GlyphLayout     layout = new GlyphLayout();
    private       Matrix4         screenProj;

    private long lastProcessedTick = -1;

    private String pickupToast;
    private float  pickupToastTimer;

    // ── Heal flash ───────────────────────────────────────────────────────────
    private float prevHp          = -1f;
    private float healFlashTimer  = 0f;
    private static final float HEAL_FLASH_DURATION = 0.5f;

    // ── Minimap ──────────────────────────────────────────────────────────────
    private static final int   MINI_SIZE          = 120;
    private static final int   MINI_MARGIN        = 10;
    private static final float MINI_ALPHA         = 0.70f;
    private boolean            minimapExpanded    = false;

    public HudRenderer(WorldState worldState,
                       InputHandler inputHandler,
                       ShapeRenderer shapes,
                       SpriteBatch batch,
                       BitmapFont font,
                       BitmapFont bigFont,
                       PreferencesPort prefs) {
        this.worldState   = worldState;
        this.inputHandler = inputHandler;
        this.shapes       = shapes;
        this.batch        = batch;
        this.font         = font;
        this.bigFont      = bigFont;
        this.prefs        = prefs;
        rebuildScreenProj();
    }

    public void resize() {
        rebuildScreenProj();
    }

    // ── Main render entry ─────────────────────────────────────────────────────

    public void render() {
        prepareFontScales();
        float           delta = Gdx.graphics.getDeltaTime();
        GameSnapshotDto snap  = worldState.getSnapshot();
        PlayerDto       me    = worldState.getLocalPlayer();
        handleHudTouches();

        if (snap != null && snap.tick > lastProcessedTick) {
            lastProcessedTick = snap.tick;
            if (me != null && me.lastPickupNotice != null && !me.lastPickupNotice.isEmpty()) {
                pickupToast      = me.lastPickupNotice;
                pickupToastTimer = 3f;
            }
        }

        if (pickupToastTimer > 0f) pickupToastTimer -= delta;

        // ── Heal flash detection ─────────────────────────────────────────────
        if (me != null && !me.isDead) {
            if (prevHp >= 0f && (me.hp - prevHp > 5f || me.isHealed)) {
                healFlashTimer = HEAL_FLASH_DURATION;
            }
            prevHp = me.hp;
        } else {
            prevHp = -1f;
        }
        if (healFlashTimer > 0f) healFlashTimer -= delta;

        if (snap == null || me == null) {
            drawConnecting();
            restoreFontScales();
            return;
        }

        if (me.isDead) {
            drawDeathOverlay(me, snap);
        } else {
            drawHealthBar(me);
            drawPlayerInfo(me);
        }

        drawTopSlots(snap, me);
        drawPickupToast();

        if (inputHandler.isAndroid()) drawTouchControls();

        drawMinimap();
        restoreFontScales();
    }

    // ── Health + speed bars ──────────────────────────────────────────────────

    private void drawHealthBar(PlayerDto me) {
        int barX = hudPx(20);
        int barY = hudPx(20);
        int barW = hudPx(200);
        int barH = hudPx(22);
        float hpFrac = Math.max(0f, Math.min(1f, me.hp / Math.max(me.maxHp, 1f)));

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(screenProj);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        shapes.setColor(0.25f, 0.25f, 0.25f, 0.5f);
        shapes.rect(barX, barY, barW, barH);
        shapes.setColor(
                hpFrac < 0.5f ? 0.9f : (2f - 2f * hpFrac) * 0.9f,
                hpFrac > 0.5f ? 0.85f : (2f * hpFrac) * 0.85f,
                0.10f, 1f);
        shapes.rect(barX, barY, barW * hpFrac, barH);

        if (me.speedTier > 0) {
            int sBarY = barY + barH + hudPx(3);
            shapes.setColor(0.12f, 0.16f, 0.26f, 0.5f);
            shapes.rect(barX, sBarY, barW, hudPx(7));
            shapes.setColor(0.20f, 0.72f, 0.95f, 1f);
            shapes.rect(barX, sBarY, barW * (me.speedTier / 10f), hudPx(7));
        }

        shapes.end();

        if (healFlashTimer > 0f) {
            float alpha = Math.min(1f, healFlashTimer / HEAL_FLASH_DURATION);
            float pulse = (float) Math.abs(Math.sin(healFlashTimer * Math.PI * 4));
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(0.10f, 0.90f, 0.30f, alpha * pulse);
            shapes.rect(barX - hudPx(3), barY - hudPx(3), barW + hudPx(6), barH + hudPx(6));
            shapes.end();
        }
    }

    // ── Player info ──────────────────────────────────────────────────────────

    private void drawPlayerInfo(PlayerDto me) {
        int barX = hudPx(20);
        int barY = hudPx(20);
        int barH = hudPx(22);
        float speedBarSpace = (me.speedTier > 0) ? hudPx(10) : 0f;
        float primY = barY + barH + speedBarSpace + hudPx(20);

        batch.setProjectionMatrix(screenProj);
        batch.begin();

        font.setColor(Color.WHITE);
        drawTextBadge((int) (barX + hudPx(4)), (int) (barY + hudPx(2)), hudPx(92), hudPx(20));
        drawShadowedText(font, (int) me.hp + " / " + (int) me.maxHp, barX + hudPx(8), barY + barH - hudPx(4));

        String reloadHint = me.isReloading
                ? "  RELOADING " + String.format("%.1f", me.reloadTimer) + "s..."
                : (me.equippedAmmo == 0 && me.equippedMags == 0 ? "  NO AMMO" : "");
        String primary = me.equippedWeaponType != null
                ? "[" + me.equippedWeaponType.name() + "]  "
                    + me.equippedAmmo + " / " + me.equippedMags + " mags" + reloadHint
                : "-";
        font.setColor(me.isReloading
                ? new Color(1f, 0.60f, 0.10f, 1f)
                : new Color(0.95f, 0.95f, 0.60f, 1f));
        layout.setText(font, primary);
        drawTextBadge((int) (barX - hudPx(4)), (int) (primY - hudPx(18)),
                (int) (layout.width + hudPx(8)), hudPx(24));
        drawShadowedText(font, primary, barX, primY);

        if (me.secondaryWeaponType != null) {
            font.setColor(new Color(0.70f, 0.70f, 0.70f, 1f));
            String secondary = me.secondaryWeaponType.name() + "  " + me.secondaryAmmo
                    + " / " + me.secondaryMags + " mags  [SPACE]";
            layout.setText(font, secondary);
            drawTextBadge((int) (barX - hudPx(4)), (int) (primY + hudPx(4)),
                    (int) (layout.width + hudPx(8)), hudPx(24));
            drawShadowedText(font, secondary, barX, primY + hudPx(22));
        }

        if (me.isReloading && me.reloadTimer > 0f) {
            batch.end();
            int barW = hudPx(200);
            float filled     = 1f - Math.min(1f, me.reloadTimer / 3f);
            float reloadBarY = primY + hudPx(46);
            shapes.setProjectionMatrix(screenProj);
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(0.30f, 0.30f, 0.30f, 0.5f);
            shapes.rect(barX, reloadBarY, barW, hudPx(6));
            shapes.setColor(1f, 0.65f, 0.10f, 1f);
            shapes.rect(barX, reloadBarY, barW * filled, hudPx(6));
            shapes.end();
            batch.setProjectionMatrix(screenProj);
            batch.begin();
        }

        batch.end();
    }

    // ── Top slots ─────────────────────────────────────────────────────────────

    private void drawTopSlots(GameSnapshotDto snap, PlayerDto me) {
        int sw = Gdx.graphics.getWidth();
        int sh = Gdx.graphics.getHeight();

        drawTopSlotPanels(snap, me, sw, sh);
        batch.setProjectionMatrix(screenProj);
        batch.begin();

        for (HudSlot slot : HudSlot.values()) {
            HudWidget widget = prefs != null ? prefs.getHudWidget(slot) : defaultWidget(slot);
            switch (widget) {
                case TIME_ALIVE:  drawTimeAliveInSlot(me, slot, sw, sh);     break;
                case LEADERBOARD: drawLeaderboardInSlot(snap, slot, sw, sh); break;
                default: break; // MINIMAP drawn separately
            }
        }

        batch.end();
    }

    private static HudWidget defaultWidget(HudSlot slot) {
        switch (slot) {
            case TOP_LEFT:   return HudWidget.LEADERBOARD;
            case TOP_CENTER: return HudWidget.TIME_ALIVE;
            default:         return HudWidget.MINIMAP;
        }
    }

    // ── Time alive ────────────────────────────────────────────────────────────

    private void drawTimeAliveInSlot(PlayerDto me, HudSlot slot, int sw, int sh) {
        if (me == null) return;
        String text = String.format("Time: %d:%02d", (int) me.timeSurvived / 60, (int) me.timeSurvived % 60);
        font.setColor(Color.LIGHT_GRAY);
        layout.setText(font, text);
        float y = slotTopY(slot, sh);
        drawTextBadge((int) (slotX(slot, layout.width, sw) - hudPx(6)), (int) (y - hudPx(18)),
                (int) (layout.width + hudPx(12)), hudPx(24));
        drawShadowedText(font, text, slotX(slot, layout.width, sw), y);
    }

    // ── Leaderboard ──────────────────────────────────────────────────────────

    private void drawLeaderboardInSlot(GameSnapshotDto snap, HudSlot slot, int sw, int sh) {
        if (snap == null || snap.players == null || snap.players.length == 0) return;

        String localId = worldState.getLocalPlayerId();

        List<PlayerDto> sorted = new ArrayList<>();
        for (PlayerDto p : snap.players) sorted.add(p);
        sorted.sort(Comparator.comparingInt((PlayerDto p) -> p.score).reversed());

        float y    = slotTopY(slot, sh);
        float rowH = hudPx(20);

        font.setColor(0.80f, 0.80f, 0.80f, 1f);
        layout.setText(font, "LEADERBOARD");
        drawTextBadge((int) (slotX(slot, layout.width, sw) - hudPx(6)),
                (int) (y - hudPx(18)), (int) (layout.width + hudPx(12)), hudPx(24));
        drawShadowedText(font, "LEADERBOARD", slotX(slot, layout.width, sw), y);
        y -= rowH;

        boolean localInTop = false;
        int shown = 0;
        for (PlayerDto p : sorted) {
            if (shown >= LEADERBOARD_ROWS) break;
            boolean isMe = localId != null && localId.equals(p.playerId);
            if (isMe) localInTop = true;
            font.setColor(isMe ? new Color(0.20f, 0.95f, 0.30f, 1f) : Color.LIGHT_GRAY);
            String line = (shown + 1) + ".  " + p.username + ":  " + p.score;
            layout.setText(font, line);
            drawTextBadge((int) (slotX(slot, layout.width, sw) - hudPx(6)),
                    (int) (y - hudPx(18)), (int) (layout.width + hudPx(12)), hudPx(24));
            drawShadowedText(font, line, slotX(slot, layout.width, sw), y);
            y -= rowH;
            shown++;
        }

        if (!localInTop && localId != null) {
            PlayerDto myEntry = null;
            int rank = 1;
            for (PlayerDto p : sorted) {
                if (localId.equals(p.playerId)) { myEntry = p; break; }
                rank++;
            }
            if (myEntry != null) {
                font.setColor(0.45f, 0.45f, 0.45f, 1f);
                layout.setText(font, "...");
                drawTextBadge((int) (slotX(slot, layout.width, sw) - hudPx(6)),
                        (int) (y - hudPx(18)), (int) (layout.width + hudPx(12)), hudPx(24));
                drawShadowedText(font, "...", slotX(slot, layout.width, sw), y);
                y -= rowH;

                font.setColor(new Color(0.20f, 0.95f, 0.30f, 1f));
                String line = rank + ".  " + myEntry.username + ":  " + myEntry.score;
                layout.setText(font, line);
                drawTextBadge((int) (slotX(slot, layout.width, sw) - hudPx(6)),
                        (int) (y - hudPx(18)), (int) (layout.width + hudPx(12)), hudPx(24));
                drawShadowedText(font, line, slotX(slot, layout.width, sw), y);
            }
        }
    }

    // ── Death overlay ─────────────────────────────────────────────────────────

    private void drawDeathOverlay(PlayerDto me, GameSnapshotDto snap) {
        int sw = Gdx.graphics.getWidth();
        int sh = Gdx.graphics.getHeight();

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapes.setProjectionMatrix(screenProj);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.62f);
        shapes.rect(0, 0, sw, sh);
        shapes.end();

        batch.setProjectionMatrix(screenProj);
        batch.begin();

        bigFont.setColor(new Color(0.9f, 0.2f, 0.2f, 1f));
        layout.setText(bigFont, "YOU DIED");
        drawShadowedText(bigFont, "YOU DIED", (sw - layout.width) / 2f, sh / 2f + 60f);

        font.setColor(Color.WHITE);
        String respawn = "Respawning in " + Math.max(1, (int) Math.ceil(me.respawnTimer)) + "...";
        layout.setText(font, respawn);
        drawShadowedText(font, respawn, (sw - layout.width) / 2f, sh / 2f - 10f);

        batch.end();
    }

    // ── Connecting ───────────────────────────────────────────────────────────

    private void drawConnecting() {
        int sw = Gdx.graphics.getWidth();
        int sh = Gdx.graphics.getHeight();
        batch.setProjectionMatrix(screenProj);
        batch.begin();
        font.setColor(Color.LIGHT_GRAY);
        layout.setText(font, "Connecting...");
        drawShadowedText(font, "Connecting...", (sw - layout.width) / 2f, sh / 2f);
        batch.end();
    }

    // ── Pickup toast ─────────────────────────────────────────────────────────

    private void drawPickupToast() {
        if (pickupToastTimer <= 0f || pickupToast == null) return;
        float alpha = Math.min(1f, pickupToastTimer);
        int sw = Gdx.graphics.getWidth();
        int sh = Gdx.graphics.getHeight();
        batch.setProjectionMatrix(screenProj);
        batch.begin();
        font.setColor(0.30f, 1f, 0.55f, alpha);
        layout.setText(font, pickupToast);
        drawTextBadge((int) (((sw - layout.width) / 2f) - hudPx(8)),
                (int) (sh * (inputHandler.isAndroid() ? 0.44f : 0.38f) - hudPx(18)),
                (int) (layout.width + hudPx(16)), hudPx(26));
        drawShadowedText(font, pickupToast, (sw - layout.width) / 2f,
                sh * (inputHandler.isAndroid() ? 0.44f : 0.38f));
        batch.end();
    }

    private void drawTopSlotPanels(GameSnapshotDto snap, PlayerDto me, int sw, int sh) {
        if (inputHandler.isAndroid()) return;

        // Time panel
        HudSlot timeSlot = widgetSlot(HudWidget.TIME_ALIVE, HudSlot.TOP_CENTER);
        if (me != null) {
            String timeText = String.format("Time: %d:%02d", (int) me.timeSurvived / 60, (int) me.timeSurvived % 60);
            layout.setText(font, timeText);
            float ty = slotTopY(timeSlot, sh);
            drawPanel(slotPanelX(timeSlot, layout.width, sw), ty - hudPx(18),
                    layout.width + hudPx(20), hudPx(28), 0.50f);
        }

        // Leaderboard panel
        if (snap != null && snap.players != null && snap.players.length > 0) {
            HudSlot lbSlot = widgetSlot(HudWidget.LEADERBOARD, HudSlot.TOP_LEFT);
            float lbWidth = hudPx(170);
            float rows    = LEADERBOARD_ROWS + 2;
            float panelY  = slotTopY(lbSlot, sh) - hudPx(18) - hudPx(20) * rows;
            drawPanel(slotPanelX(lbSlot, lbWidth, sw), panelY,
                    lbWidth + hudPx(20), hudPx(22) * rows + hudPx(12), 0.50f);
        }
    }

    // ── Touch controls (Android only) ────────────────────────────────────────

    private void drawTouchControls() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapes.setProjectionMatrix(screenProj);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        inputHandler.getMoveStick().render(shapes);
        inputHandler.getAimStick().render(shapes);
        shapes.setColor(0.95f, 0.95f, 0.95f, 0.12f);
        shapes.circle(inputHandler.getAimStick().getBaseX(), inputHandler.getAimStick().getBaseY(),
                inputHandler.getAimFireZoneRadius(), 32);
        shapes.setColor(0.92f, 0.22f, 0.22f, 0.86f);
        shapes.circle(inputHandler.getAimStick().getBaseX(), inputHandler.getAimStick().getBaseY(),
                inputHandler.getAimFireZoneRadius(), 28);
        shapes.setColor(0.68f, 0.12f, 0.12f, 0.96f);
        shapes.circle(inputHandler.getAimStick().getBaseX(), inputHandler.getAimStick().getBaseY(),
                Math.max(10f, inputHandler.getAimFireZoneRadius() - hudPx(6)), 28);
        shapes.setColor(0.55f, 0.30f, 0.80f, 0.75f);
        shapes.circle(inputHandler.getSwitchBtnX(), inputHandler.getSwitchBtnY(), inputHandler.getSwitchBtnR(), 24);
        shapes.setColor(0.35f, 0.15f, 0.55f, 0.85f);
        shapes.circle(inputHandler.getSwitchBtnX(), inputHandler.getSwitchBtnY(), inputHandler.getSwitchBtnR() - 5f, 24);
        shapes.setColor(0.85f, 0.45f, 0.10f, 0.80f);
        shapes.circle(inputHandler.getReloadBtnX(), inputHandler.getReloadBtnY(), inputHandler.getReloadBtnR(), 24);
        shapes.setColor(0.60f, 0.28f, 0.05f, 0.90f);
        shapes.circle(inputHandler.getReloadBtnX(), inputHandler.getReloadBtnY(), inputHandler.getReloadBtnR() - 5f, 24);
        shapes.end();

        batch.setProjectionMatrix(screenProj);
        batch.begin();
        font.setColor(Color.WHITE);
        layout.setText(font, "SWAP");
        drawShadowedText(font, "SWAP",
                inputHandler.getSwitchBtnX() - layout.width / 2f,
                inputHandler.getSwitchBtnY() + layout.height / 2f);
        layout.setText(font, "LOAD");
        drawShadowedText(font, "LOAD",
                inputHandler.getReloadBtnX() - layout.width / 2f,
                inputHandler.getReloadBtnY() + layout.height / 2f);
        layout.setText(font, "FIRE");
        drawShadowedText(font, "FIRE",
                inputHandler.getAimStick().getBaseX() - layout.width / 2f,
                inputHandler.getAimStick().getBaseY() + layout.height / 2f);
        batch.end();
    }

    // ── Minimap ───────────────────────────────────────────────────────────────

    private void drawMinimap() {
        MapDto          map  = worldState.getMap();
        GameSnapshotDto snap = worldState.getSnapshot();
        if (map == null || map.tiles == null) return;

        int sw       = Gdx.graphics.getWidth();
        int sh       = Gdx.graphics.getHeight();
        int miniSize = minimapExpanded
                ? Math.round(Math.min(sw, sh) * 0.80f)
                : hudPx(MINI_SIZE);
        float miniX  = minimapX(sw, miniSize);
        float miniY  = minimapExpanded ? (sh - miniSize) * 0.5f : currentMinimapY(sh);
        float scaleX = (float) miniSize / map.width;
        float scaleY = (float) miniSize / map.height;
        String localId = worldState.getLocalPlayerId();
        float bgAlpha  = minimapExpanded ? 0.56f : MINI_ALPHA;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(screenProj);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, bgAlpha);
        shapes.rect(miniX, miniY, miniSize, miniSize);

        for (int ty = 0; ty < map.height; ty++) {
            for (int tx = 0; tx < map.width; tx++) {
                TileType tile = map.tiles[ty * map.width + tx];
                if (tile == null || tile == TileType.FLOOR) continue;
                switch (tile) {
                    case WALL:   shapes.setColor(0.40f, 0.40f, 0.46f, 1f); break;
                    case WINDOW:
                        if (!minimapExpanded) continue;
                        shapes.setColor(0.25f, 0.70f, 0.90f, 0.85f); break;
                    case TRAP:
                        if (!minimapExpanded) continue;
                        shapes.setColor(0.90f, 0.40f, 0.10f, 0.85f); break;
                    case COBWEB:
                        if (!minimapExpanded) continue;
                        shapes.setColor(0.60f, 0.55f, 0.45f, 0.80f); break;
                    default: continue;
                }
                shapes.rect(miniX + tx * scaleX, miniY + ty * scaleY,
                        Math.max(1f, scaleX), Math.max(1f, scaleY));
            }
        }

        if (snap != null && snap.chests != null) {
            float cs = Math.max(2.5f, scaleX * 0.9f);
            for (ChestDto chest : snap.chests) {
                if (chest == null || chest.pos == null) continue;
                shapes.setColor(chest.isOpen
                        ? new Color(0.55f, 0.55f, 0.55f, 0.9f)
                        : new Color(1.00f, 0.82f, 0.10f, 1.0f));
                shapes.rect(miniX + chest.pos.x * scaleX - cs * 0.5f,
                        miniY + chest.pos.y * scaleY - cs * 0.5f, cs, cs);
            }
        }

        if (snap != null && snap.players != null) {
            float pr = Math.max(2f, scaleX * 0.65f);
            for (PlayerDto p : snap.players) {
                if (p == null || p.pos == null || p.isDead) continue;
                boolean isMe = localId != null && localId.equals(p.playerId);
                shapes.setColor(isMe
                        ? new Color(0.20f, 0.95f, 0.30f, 1f)
                        : new Color(0.95f, 0.20f, 0.20f, 1f));
                shapes.circle(miniX + p.pos.x * scaleX, miniY + p.pos.y * scaleY, pr, 8);
            }
        }

        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(1f, 1f, 1f, 0.80f);
        shapes.rect(miniX, miniY, miniSize, miniSize);
        shapes.end();

        batch.setProjectionMatrix(screenProj);
        batch.begin();
        font.setColor(0.65f, 0.65f, 0.65f, 0.65f);
        drawShadowedText(font, minimapExpanded ? "[TAP]" : "[M/TAP]", miniX + hudPx(3), miniY + miniSize - hudPx(2));
        batch.end();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private float slotX(HudSlot slot, float textWidth, int sw) {
        switch (slot) {
            case TOP_LEFT:   return hudSideMargin();
            case TOP_CENTER: return (sw - textWidth) / 2f;
            default:         return sw - textWidth - hudSideMargin();
        }
    }

    private float slotPanelX(HudSlot slot, float contentWidth, int sw) {
        switch (slot) {
            case TOP_LEFT:   return hudSideMargin() - hudPx(10);
            case TOP_CENTER: return (sw - contentWidth) / 2f - hudPx(10);
            default:         return sw - contentWidth - hudSideMargin() - hudPx(10);
        }
    }

    private float slotTopY(HudSlot slot, int sh) {
        if (inputHandler.isAndroid() && slot != HudSlot.TOP_CENTER) {
            return inputHandler.getPauseBtnY() - hudPx(12);
        }
        return sh - hudTopMargin();
    }

    private HudSlot widgetSlot(HudWidget widget, HudSlot fallback) {
        if (prefs != null) {
            for (HudSlot s : HudSlot.values()) {
                if (prefs.getHudWidget(s) == widget) return s;
            }
        }
        return fallback;
    }

    private float minimapX(int sw, int miniSize) {
        if (minimapExpanded) return (sw - miniSize) * 0.5f;
        switch (widgetSlot(HudWidget.MINIMAP, HudSlot.TOP_RIGHT)) {
            case TOP_LEFT:   return hudPx(MINI_MARGIN + 12);
            case TOP_CENTER: return (sw - miniSize) * 0.5f;
            default:         return sw - miniSize - hudPx(MINI_MARGIN + 12);
        }
    }

    private void handleHudTouches() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.M)) {
            minimapExpanded = !minimapExpanded;
        }
        if (Gdx.input.justTouched()) {
            int tx = Gdx.input.getX();
            int ty = Gdx.graphics.getHeight() - Gdx.input.getY();
            if (isInsideMinimap(tx, ty)) {
                minimapExpanded = !minimapExpanded;
            }
        }
    }

    private boolean isInsideMinimap(float x, float y) {
        int sw = Gdx.graphics.getWidth();
        int sh = Gdx.graphics.getHeight();
        int miniSize = minimapExpanded
                ? Math.round(Math.min(sw, sh) * 0.80f)
                : hudPx(MINI_SIZE);
        float miniX = minimapX(sw, miniSize);
        float miniY = minimapExpanded ? (sh - miniSize) * 0.5f : currentMinimapY(sh);
        return x >= miniX && x <= miniX + miniSize && y >= miniY && y <= miniY + miniSize;
    }

    private void rebuildScreenProj() {
        if (screenProj == null) screenProj = new Matrix4();
        screenProj.setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    private void drawPanel(float x, float y, float w, float h, float alpha) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(screenProj);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.28f, 0.30f, 0.34f, alpha);
        shapes.rect(x, y, w, h);
        shapes.end();
    }

    private void drawTextBadge(int x, int y, int w, int h) {
        batch.end();
        drawPanel(x, y, w, h, 0.50f);
        batch.begin();
    }

    private void prepareFontScales() {
        font.getData().setScale(hudFontScale());
        bigFont.getData().setScale(hudBigFontScale());
    }

    private void restoreFontScales() {
        font.getData().setScale(1.4f);
        bigFont.getData().setScale(3.5f);
    }

    private float hudFontScale() {
        return inputHandler.isAndroid() ? 1.4f * hudScale() : 1.4f;
    }

    private float hudBigFontScale() {
        return inputHandler.isAndroid() ? 3.5f * Math.max(1f, hudScale() * 0.92f) : 3.5f;
    }

    private float hudScale() {
        float shortSide = Math.min(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        return Math.max(1.0f, Math.min(1.75f, shortSide / 720f));
    }

    private int hudPx(int value) {
        return Math.round(value * hudScale());
    }

    private float currentMinimapY(int sh) {
        int miniSize = minimapExpanded
                ? Math.round(Math.min(Gdx.graphics.getWidth(), sh) * 0.80f)
                : hudPx(MINI_SIZE);
        return minimapExpanded ? (sh - miniSize) * 0.5f : sh - miniSize - hudPx(MINI_MARGIN);
    }

    private float hudSideMargin() {
        return inputHandler.isAndroid() ? hudPx(36) : hudPx(20);
    }

    private float hudTopMargin() {
        return inputHandler.isAndroid() ? hudPx(44) : hudPx(20);
    }

    private void drawShadowedText(BitmapFont bitmapFont, String text, float x, float y) {
        Color original = bitmapFont.getColor().cpy();
        bitmapFont.setColor(0f, 0f, 0f, original.a * 0.42f);
        bitmapFont.draw(batch, text, x + hudPx(2), y - hudPx(2));
        bitmapFont.setColor(original);
        bitmapFont.draw(batch, text, x, y);
    }
}
