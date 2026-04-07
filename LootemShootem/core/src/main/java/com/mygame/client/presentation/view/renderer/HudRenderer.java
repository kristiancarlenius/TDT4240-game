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
import java.util.LinkedList;
import java.util.List;

public final class HudRenderer {

    private static final int MAX_KILL_FEED = 5;
    private static final int LEADERBOARD_ROWS = 9; // plus current player if outside top 9

    private final WorldState      worldState;
    private final InputHandler    inputHandler;
    private final PreferencesPort prefs;
    private final ShapeRenderer   shapes;
    private final SpriteBatch     batch;
    private final BitmapFont      font;
    private final BitmapFont      bigFont;
    private final GlyphLayout     layout = new GlyphLayout();
    private       Matrix4         screenProj;

    private final LinkedList<String> killFeed          = new LinkedList<>();
    private long                     lastProcessedTick = -1;

    private String pickupToast;
    private float  pickupToastTimer;

    // ── Leaderboard mode cycling ─────────────────────────────────────────────
    /** 0 = kills session, 1 = kills this life, 2 = total score (kills + minutes). */
    private int   leaderboardMode     = 0;
    private static final int LB_MODES = 3;
    /** Tracks touch to avoid repeated cycling on a held tap. */
    private boolean lbTouchConsumed   = false;

    // ── Heal flash ───────────────────────────────────────────────────────────
    private float prevHp          = -1f;
    private float healFlashTimer  = 0f;
    private static final float HEAL_FLASH_DURATION = 0.5f;

    // ── Minimap ──────────────────────────────────────────────────────────────
    private static final int   MINI_SIZE         = 120;
    private static final int   MINI_SIZE_EXPANDED = 240;
    private static final int   MINI_MARGIN       = 10;
    private static final float MINI_ALPHA        = 0.70f;
    private boolean            minimapExpanded   = false;

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
        float           delta = Gdx.graphics.getDeltaTime();
        GameSnapshotDto snap  = worldState.getSnapshot();
        PlayerDto       me    = worldState.getLocalPlayer();

        if (snap != null && snap.tick > lastProcessedTick) {
            lastProcessedTick = snap.tick;
            if (snap.killFeed != null) {
                for (String msg : snap.killFeed) {
                    if (msg != null && !msg.isEmpty()) {
                        killFeed.addLast(msg);
                        while (killFeed.size() > MAX_KILL_FEED) killFeed.removeFirst();
                    }
                }
            }
            if (me != null && me.lastPickupNotice != null && !me.lastPickupNotice.isEmpty()) {
                pickupToast      = me.lastPickupNotice;
                pickupToastTimer = 3f;
            }
        }

        if (pickupToastTimer > 0f) pickupToastTimer -= delta;

        // ── Leaderboard mode: Tab on desktop, tap on leaderboard header on mobile ──
        if (Gdx.input.isKeyJustPressed(Input.Keys.TAB)) {
            leaderboardMode = (leaderboardMode + 1) % LB_MODES;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.M)) {
            minimapExpanded = !minimapExpanded;
        }
        if (Gdx.input.justTouched()) {
            int tx = Gdx.input.getX();
            int ty = Gdx.graphics.getHeight() - Gdx.input.getY();
            int sw = Gdx.graphics.getWidth();
            int sh = Gdx.graphics.getHeight();
            // Tap zone: top-right corner (leaderboard area)
            if (tx > sw * 0.6f && ty > sh * 0.82f) {
                if (!lbTouchConsumed) {
                    leaderboardMode = (leaderboardMode + 1) % LB_MODES;
                    lbTouchConsumed = true;
                }
            } else {
                lbTouchConsumed = false;
            }
        } else if (!Gdx.input.isTouched()) {
            lbTouchConsumed = false;
        }

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
            return;
        }

        if (me.isDead) {
            drawDeathOverlay(me, snap);
        } else {
            drawHealthBar(me);
            drawPlayerInfo(me);
        }

        // Top slot widgets — always visible even when dead
        drawTopSlots(snap, me);

        drawPickupToast();

        if (inputHandler.isAndroid()) drawTouchControls();

        drawMinimap();
    }

    // ── Health + speed bars ──────────────────────────────────────────────────

    private static final int BAR_X = 20;
    private static final int BAR_Y = 20;
    private static final int BAR_W = 200;
    private static final int BAR_H = 22;

    private void drawHealthBar(PlayerDto me) {
        float hpFrac = Math.max(0f, Math.min(1f, me.hp / Math.max(me.maxHp, 1f)));

        shapes.setProjectionMatrix(screenProj);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        // HP bar
        shapes.setColor(0.25f, 0.25f, 0.25f, 1f);
        shapes.rect(BAR_X, BAR_Y, BAR_W, BAR_H);
        shapes.setColor(
                hpFrac < 0.5f ? 0.9f : (2f - 2f * hpFrac) * 0.9f,
                hpFrac > 0.5f ? 0.85f : (2f * hpFrac) * 0.85f,
                0.10f, 1f);
        shapes.rect(BAR_X, BAR_Y, BAR_W * hpFrac, BAR_H);

        // Speed tier bar — thin blue strip directly above HP bar
        if (me.speedTier > 0) {
            int sBarY = BAR_Y + BAR_H + 3;
            shapes.setColor(0.12f, 0.16f, 0.26f, 1f); // dark background
            shapes.rect(BAR_X, sBarY, BAR_W, 7);
            shapes.setColor(0.20f, 0.72f, 0.95f, 1f); // blue fill
            shapes.rect(BAR_X, sBarY, BAR_W * (me.speedTier / 10f), 7);
        }

        shapes.end();

        // Heal flash — green pulsing border around HP bar (needs blending)
        if (healFlashTimer > 0f) {
            float alpha = Math.min(1f, healFlashTimer / HEAL_FLASH_DURATION);
            float pulse = (float) Math.abs(Math.sin(healFlashTimer * Math.PI * 4));
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            shapes.setProjectionMatrix(screenProj);
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(0.10f, 0.90f, 0.30f, alpha * pulse);
            shapes.rect(BAR_X - 3, BAR_Y - 3, BAR_W + 6, BAR_H + 6);
            shapes.end();
        }
    }

    // ── Player info — bottom-left (weapon info, HP number inside bar) ────────

    private void drawPlayerInfo(PlayerDto me) {
        // Space occupied by the optional speed bar (7px bar + 3px gap above HP bar)
        float speedBarSpace = (me.speedTier > 0) ? 10f : 0f;
        // Primary weapon text sits above the bars
        float primY = BAR_Y + BAR_H + speedBarSpace + 20f;

        batch.setProjectionMatrix(screenProj);
        batch.begin();

        // HP number drawn inside the HP bar
        font.setColor(Color.WHITE);
        font.draw(batch, (int) me.hp + " / " + (int) me.maxHp, BAR_X + 8f, BAR_Y + BAR_H - 4f);

        // Primary weapon line
        String reloadHint = me.isReloading
                ? "  RELOADING " + String.format("%.1f", me.reloadTimer) + "s..."
                : (me.equippedAmmo == 0 && me.equippedMags == 0 ? "  NO AMMO" : "");
        String primary = me.equippedWeaponType != null
                ? "[" + me.equippedWeaponType.name() + "]  "
                    + me.equippedAmmo + " / " + me.equippedMags + " mags" + reloadHint
                : "—";
        font.setColor(me.isReloading
                ? new Color(1f, 0.60f, 0.10f, 1f)
                : new Color(0.95f, 0.95f, 0.60f, 1f));
        font.draw(batch, primary, BAR_X, primY);

        // Secondary weapon line (above primary)
        if (me.secondaryWeaponType != null) {
            font.setColor(new Color(0.70f, 0.70f, 0.70f, 1f));
            font.draw(batch, me.secondaryWeaponType.name() + "  " + me.secondaryAmmo
                    + " / " + me.secondaryMags + " mags  [SPACE]", BAR_X, primY + 22f);
        }

        // Reload progress bar — above the secondary weapon line
        if (me.isReloading && me.reloadTimer > 0f) {
            batch.end();
            float filled     = 1f - Math.min(1f, me.reloadTimer / 3f);
            float reloadBarY = primY + 46f;
            shapes.setProjectionMatrix(screenProj);
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(0.30f, 0.30f, 0.30f, 1f);
            shapes.rect(BAR_X, reloadBarY, BAR_W, 6f);
            shapes.setColor(1f, 0.65f, 0.10f, 1f);
            shapes.rect(BAR_X, reloadBarY, BAR_W * filled, 6f);
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

        batch.setProjectionMatrix(screenProj);
        batch.begin();

        for (HudSlot slot : HudSlot.values()) {
            HudWidget widget = prefs != null ? prefs.getHudWidget(slot) : defaultWidget(slot);
            switch (widget) {
                case KILL_FEED:   drawKillFeedInSlot(slot, sw, sh);        break;
                case TIME_ALIVE:  drawTimeAliveInSlot(me, slot, sw, sh);   break;
                case LEADERBOARD: drawLeaderboardInSlot(snap, slot, sw, sh); break;
            }
        }

        batch.end();
    }

    private static HudWidget defaultWidget(HudSlot slot) {
        switch (slot) {
            case TOP_LEFT:   return HudWidget.KILL_FEED;
            case TOP_CENTER: return HudWidget.TIME_ALIVE;
            case TOP_RIGHT:  return HudWidget.LEADERBOARD;
            default:         return HudWidget.KILL_FEED;
        }
    }

    // ── Kill feed ──────────────────────────────────────────────────────────────

    private void drawKillFeedInSlot(HudSlot slot, int sw, int sh) {
        if (killFeed.isEmpty()) return;
        float y = sh - 20f;
        for (String msg : killFeed) {
            font.setColor(1f, 0.85f, 0.40f, 1f);
            layout.setText(font, msg);
            font.draw(batch, msg, slotX(slot, layout.width, sw), y);
            y -= 22f;
        }
    }

    // ── Time alive ────────────────────────────────────────────────────────────

    private void drawTimeAliveInSlot(PlayerDto me, HudSlot slot, int sw, int sh) {
        if (me == null) return;
        int mins = (int) me.timeSurvived / 60;
        int secs = (int) me.timeSurvived % 60;
        String text = String.format("Time: %d:%02d", mins, secs);
        font.setColor(Color.LIGHT_GRAY);
        layout.setText(font, text);
        font.draw(batch, text, slotX(slot, layout.width, sw), sh - 20f);
    }

    // ── Leaderboard (3 modes, Tab / tap to cycle) ────────────────────────────

    private void drawLeaderboardInSlot(GameSnapshotDto snap, HudSlot slot, int sw, int sh) {
        if (snap == null || snap.players == null || snap.players.length == 0) return;

        String localId = worldState.getLocalPlayerId();

        // Determine comparator and value extractor per mode
        Comparator<PlayerDto> cmp;
        String modeLabel;
        switch (leaderboardMode) {
            case 1:
                cmp       = Comparator.comparingInt((PlayerDto p) -> p.killsThisLife);
                modeLabel = "KILLS THIS LIFE";
                break;
            case 2:
                cmp       = Comparator.comparingInt((PlayerDto p) -> p.score + (int)(p.timeSurvived / 60f));
                modeLabel = "TOTAL SCORE";
                break;
            default:
                cmp       = Comparator.comparingInt((PlayerDto p) -> p.score);
                modeLabel = "KILLS SESSION";
                break;
        }

        List<PlayerDto> sorted = new ArrayList<>();
        for (PlayerDto p : snap.players) sorted.add(p);
        sorted.sort(cmp.reversed());

        float y    = sh - 20f;
        float rowH = 20f;

        // Header with mode label and cycle hint
        String header = modeLabel + " [TAB]";
        font.setColor(0.80f, 0.80f, 0.80f, 1f);
        layout.setText(font, header);
        font.draw(batch, header, slotX(slot, layout.width, sw), y);
        y -= rowH;

        // Top LEADERBOARD_ROWS entries
        boolean localInTop = false;
        int shown = 0;
        for (PlayerDto p : sorted) {
            if (shown >= LEADERBOARD_ROWS) break;
            boolean isMe = localId != null && localId.equals(p.playerId);
            if (isMe) localInTop = true;
            int value = lbValue(p);
            font.setColor(isMe ? new Color(0.20f, 0.95f, 0.30f, 1f) : Color.LIGHT_GRAY);
            String line = (shown + 1) + ".  " + p.username + ":  " + value;
            layout.setText(font, line);
            font.draw(batch, line, slotX(slot, layout.width, sw), y);
            y -= rowH;
            shown++;
        }

        // If current player is outside top rows, always append them
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
                font.draw(batch, "...", slotX(slot, layout.width, sw), y);
                y -= rowH;

                font.setColor(new Color(0.20f, 0.95f, 0.30f, 1f));
                String line = rank + ".  " + myEntry.username + ":  " + lbValue(myEntry);
                layout.setText(font, line);
                font.draw(batch, line, slotX(slot, layout.width, sw), y);
            }
        }
    }

    private int lbValue(PlayerDto p) {
        switch (leaderboardMode) {
            case 1:  return p.killsThisLife;
            case 2:  return p.score * 10 + (int)(p.timeSurvived / 10f);
            default: return p.score;
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
        bigFont.draw(batch, "YOU DIED", (sw - layout.width) / 2f, sh / 2f + 60f);

        font.setColor(Color.WHITE);
        String respawn = "Respawning in " + Math.max(1, (int) Math.ceil(me.respawnTimer)) + "...";
        layout.setText(font, respawn);
        font.draw(batch, respawn, (sw - layout.width) / 2f, sh / 2f - 10f);

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
        font.draw(batch, "Connecting...", (sw - layout.width) / 2f, sh / 2f);
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
        font.draw(batch, pickupToast, (sw - layout.width) / 2f, sh * 0.38f);
        batch.end();
    }

    // ── Touch controls (Android only) ────────────────────────────────────────

    private void drawTouchControls() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapes.setProjectionMatrix(screenProj);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        inputHandler.moveStick.render(shapes);
        inputHandler.aimStick.render(shapes);
        shapes.setColor(0.55f, 0.30f, 0.80f, 0.75f);
        shapes.circle(inputHandler.switchBtnX, inputHandler.switchBtnY, inputHandler.switchBtnR, 24);
        shapes.setColor(0.35f, 0.15f, 0.55f, 0.85f);
        shapes.circle(inputHandler.switchBtnX, inputHandler.switchBtnY, inputHandler.switchBtnR - 5f, 24);
        shapes.setColor(0.85f, 0.45f, 0.10f, 0.80f);
        shapes.circle(inputHandler.reloadBtnX, inputHandler.reloadBtnY, inputHandler.reloadBtnR, 24);
        shapes.setColor(0.60f, 0.28f, 0.05f, 0.90f);
        shapes.circle(inputHandler.reloadBtnX, inputHandler.reloadBtnY, inputHandler.reloadBtnR - 5f, 24);
        shapes.end();

        batch.setProjectionMatrix(screenProj);
        batch.begin();
        font.setColor(Color.WHITE);
        layout.setText(font, "SW");
        font.draw(batch, "SW",
                inputHandler.switchBtnX - layout.width / 2f,
                inputHandler.switchBtnY + layout.height / 2f);
        layout.setText(font, "R");
        font.draw(batch, "R",
                inputHandler.reloadBtnX - layout.width / 2f,
                inputHandler.reloadBtnY + layout.height / 2f);
        batch.end();
    }

    // ── Minimap ───────────────────────────────────────────────────────────────

    private void drawMinimap() {
        MapDto          map    = worldState.getMap();
        GameSnapshotDto snap   = worldState.getSnapshot();
        if (map == null || map.tiles == null) return;

        int sw       = Gdx.graphics.getWidth();
        int miniSize = minimapExpanded ? MINI_SIZE_EXPANDED : MINI_SIZE;
        float miniX  = sw - miniSize - MINI_MARGIN;
        float miniY  = MINI_MARGIN;
        float scaleX = (float) miniSize / map.width;
        float scaleY = (float) miniSize / map.height;
        String localId = worldState.getLocalPlayerId();

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(screenProj);

        // ── Background ──────────────────────────────────────────────────────
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, MINI_ALPHA);
        shapes.rect(miniX, miniY, miniSize, miniSize);

        // ── Tiles ───────────────────────────────────────────────────────────
        for (int ty = 0; ty < map.height; ty++) {
            for (int tx = 0; tx < map.width; tx++) {
                TileType tile = map.tiles[ty * map.width + tx];
                if (tile == null || tile == TileType.FLOOR) continue;
                switch (tile) {
                    case WALL:
                        shapes.setColor(0.40f, 0.40f, 0.46f, 1f);
                        break;
                    case WINDOW:
                        if (!minimapExpanded) continue;
                        shapes.setColor(0.25f, 0.70f, 0.90f, 0.85f);
                        break;
                    case TRAP:
                        if (!minimapExpanded) continue;
                        shapes.setColor(0.90f, 0.40f, 0.10f, 0.85f);
                        break;
                    case COBWEB:
                        if (!minimapExpanded) continue;
                        shapes.setColor(0.60f, 0.55f, 0.45f, 0.80f);
                        break;
                    default:
                        continue;
                }
                shapes.rect(
                    miniX + tx * scaleX,
                    miniY + ty * scaleY,
                    Math.max(1f, scaleX),
                    Math.max(1f, scaleY));
            }
        }

        // ── Chests ──────────────────────────────────────────────────────────
        if (snap != null && snap.chests != null) {
            float cs = Math.max(2.5f, scaleX * 0.9f);
            for (ChestDto chest : snap.chests) {
                if (chest == null || chest.pos == null) continue;
                float cx = miniX + chest.pos.x * scaleX;
                float cy = miniY + chest.pos.y * scaleY;
                shapes.setColor(chest.isOpen
                    ? new Color(0.55f, 0.55f, 0.55f, 0.9f)
                    : new Color(1.00f, 0.82f, 0.10f, 1.0f));
                shapes.rect(cx - cs * 0.5f, cy - cs * 0.5f, cs, cs);
            }
        }

        // ── Players ─────────────────────────────────────────────────────────
        if (snap != null && snap.players != null) {
            float pr = Math.max(2f, scaleX * 0.65f);
            for (PlayerDto p : snap.players) {
                if (p == null || p.pos == null || p.isDead) continue;
                boolean isMe = localId != null && localId.equals(p.playerId);
                float px = miniX + p.pos.x * scaleX;
                float py = miniY + p.pos.y * scaleY;
                shapes.setColor(isMe
                    ? new Color(0.20f, 0.95f, 0.30f, 1f)
                    : new Color(0.95f, 0.20f, 0.20f, 1f));
                shapes.circle(px, py, pr, 8);
            }
        }

        shapes.end();

        // ── Border ──────────────────────────────────────────────────────────
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(1f, 1f, 1f, 0.80f);
        shapes.rect(miniX, miniY, miniSize, miniSize);
        shapes.end();

        // ── "M" hint label ──────────────────────────────────────────────────
        batch.setProjectionMatrix(screenProj);
        batch.begin();
        font.setColor(0.65f, 0.65f, 0.65f, 0.65f);
        font.draw(batch, "[M]", miniX + 3f, miniY + miniSize - 2f);
        batch.end();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Returns the x coordinate at which to start drawing text of the given width
     * so that it is left-aligned, centered, or right-aligned for the slot.
     */
    private float slotX(HudSlot slot, float textWidth, int sw) {
        switch (slot) {
            case TOP_LEFT:   return 20f;
            case TOP_CENTER: return (sw - textWidth) / 2f;
            case TOP_RIGHT:  return sw - textWidth - 20f;
            default:         return 20f;
        }
    }

    private void rebuildScreenProj() {
        if (screenProj == null) screenProj = new Matrix4();
        screenProj.setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }
}
