package com.mygame.client.presentation.view.renderer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.mygame.client.domain.model.WorldState;
import com.mygame.client.presentation.view.input.InputHandler;
import com.mygame.shared.dto.GameSnapshotDto;
import com.mygame.shared.dto.PlayerDto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

public final class HudRenderer {

    /** Maximum number of kill-feed lines kept in memory and always displayed. */
    private static final int MAX_KILL_FEED = 5;

    private final WorldState   worldState;
    private final InputHandler inputHandler;
    private final ShapeRenderer shapes;
    private final SpriteBatch  batch;
    private final BitmapFont   font;
    private final BitmapFont   bigFont;
    private final GlyphLayout  layout = new GlyphLayout();
    private       Matrix4      screenProj;

    /** Last N kill messages – never expire, capped at MAX_KILL_FEED. */
    private final LinkedList<String> killFeed          = new LinkedList<>();
    private long                     lastProcessedTick = -1;

    private String pickupToast;
    private float  pickupToastTimer;

    public HudRenderer(WorldState worldState,
                       InputHandler inputHandler,
                       ShapeRenderer shapes,
                       SpriteBatch batch,
                       BitmapFont font,
                       BitmapFont bigFont) {
        this.worldState   = worldState;
        this.inputHandler = inputHandler;
        this.shapes       = shapes;
        this.batch        = batch;
        this.font         = font;
        this.bigFont      = bigFont;
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

        // Ingest new kill-feed entries and pickup notices from the server (once per tick).
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

        // Always drawn regardless of alive/dead state
        drawLeaderboard(snap);
        drawKillFeed();
        drawPickupToast();

        if (inputHandler.isAndroid()) drawTouchControls();
    }

    // ── Health bar ───────────────────────────────────────────────────────────

    private void drawHealthBar(PlayerDto me) {
        float hpFrac = Math.max(0f, Math.min(1f, me.hp / 100f));
        int barX = 20, barY = 20, barW = 200, barH = 18;

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
    }

    // ── Player info (alive) ──────────────────────────────────────────────────

    private void drawPlayerInfo(PlayerDto me) {
        int sh   = Gdx.graphics.getHeight();
        int barY = 20, barH = 18;

        batch.setProjectionMatrix(screenProj);
        batch.begin();

        font.setColor(Color.WHITE);
        font.draw(batch, "HP  " + (int) me.hp + " / 100", 20, barY + barH + 18f);

        // Equipped weapon
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
        font.draw(batch, primary, 20, barY + barH + 36f);

        // Reload progress bar
        if (me.isReloading && me.reloadTimer > 0f) {
            batch.end();
            float maxReload = me.reloadTimer; // approximate; server sends remaining
            float filled    = 1f - Math.min(1f, me.reloadTimer / 3f); // rough visual
            shapes.setProjectionMatrix(screenProj);
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(0.30f, 0.30f, 0.30f, 1f);
            shapes.rect(20, barY + barH + 16f, 200f, 6f);
            shapes.setColor(1f, 0.65f, 0.10f, 1f);
            shapes.rect(20, barY + barH + 16f, 200f * filled, 6f);
            shapes.end();
            batch.setProjectionMatrix(screenProj);
            batch.begin();
        }

        if (me.secondaryWeaponType != null) {
            font.setColor(new Color(0.70f, 0.70f, 0.70f, 1f));
            font.draw(batch, me.secondaryWeaponType.name() + "  " + me.secondaryAmmo
                    + " / " + me.secondaryMags + " mags  [SPACE]", 20, barY + barH + 56f);
        }

        if (me.speedTier > 0) {
            font.setColor(new Color(0.20f, 0.85f, 0.95f, 1f));
            String speedLabel = "SPD TIER " + me.speedTier + " / 5  (x"
                    + String.format("%.2f", 1f + me.speedTier * 0.15f) + ")";
            font.draw(batch, speedLabel,
                    20, barY + barH + (me.secondaryWeaponType != null ? 76f : 56f));
        }
        if (me.maxHp > 100f) {
            font.setColor(new Color(0.90f, 0.45f, 0.90f, 1f));
            float nextRow = barY + barH + (me.secondaryWeaponType != null ? 76f : 56f)
                    + (me.speedTier > 0 ? 20f : 0f);
            font.draw(batch, "MAX HP " + (int) me.maxHp + "  (tier " + me.healthTier + " / 10)",
                    20, nextRow);
        }

        // Own kill count + time survived (top-right, above the leaderboard)
        int sw = Gdx.graphics.getWidth();
        font.setColor(new Color(1f, 0.85f, 0.2f, 1f));
        String scoreText = "Kills: " + me.score;
        layout.setText(font, scoreText);
        font.draw(batch, scoreText, sw - layout.width - 20f, sh - 20f);

        font.setColor(Color.LIGHT_GRAY);
        int mins = (int) me.timeSurvived / 60;
        int secs = (int) me.timeSurvived % 60;
        String timeText = String.format("Time: %d:%02d", mins, secs);
        layout.setText(font, timeText);
        font.draw(batch, timeText, sw - layout.width - 20f, sh - 42f);

        batch.end();
    }

    // ── Leaderboard (always visible, sorted by total session kills) ───────────

    private void drawLeaderboard(GameSnapshotDto snap) {
        if (snap == null || snap.players == null || snap.players.length == 0) return;

        // Sort descending by score (session kills)
        List<PlayerDto> sorted = new ArrayList<>();
        for (PlayerDto p : snap.players) sorted.add(p);
        sorted.sort(Comparator.comparingInt((PlayerDto p) -> p.score).reversed());

        int   sw     = Gdx.graphics.getWidth();
        int   sh     = Gdx.graphics.getHeight();
        float lbTop  = sh - 68f;   // below own stats header
        float rowH   = 20f;
        String localId = worldState.getLocalPlayerId();

        batch.setProjectionMatrix(screenProj);
        batch.begin();

        font.setColor(new Color(0.80f, 0.80f, 0.80f, 1f));
        String header = "── LEADERBOARD ──";
        layout.setText(font, header);
        font.draw(batch, header, sw - layout.width - 20f, lbTop);

        float rowY = lbTop - rowH;
        for (PlayerDto p : sorted) {
            boolean isMe = localId != null && localId.equals(p.playerId);
            font.setColor(isMe ? new Color(0.20f, 0.95f, 0.30f, 1f)
                               : Color.LIGHT_GRAY);
            String line = (isMe ? "► " : "  ") + p.username + ":  " + p.score + " kills";
            layout.setText(font, line);
            font.draw(batch, line, sw - layout.width - 20f, rowY);
            rowY -= rowH;
        }

        batch.end();
    }

    // ── Kill feed (last MAX_KILL_FEED entries, always visible) ────────────────

    private void drawKillFeed() {
        if (killFeed.isEmpty()) return;
        int sh = Gdx.graphics.getHeight();

        batch.setProjectionMatrix(screenProj);
        batch.begin();

        float feedY = sh * 0.55f;
        for (String msg : killFeed) {
            font.setColor(1f, 0.85f, 0.40f, 1f);
            font.draw(batch, msg, 20f, feedY);
            feedY += 22f;
        }

        batch.end();
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
        // Leaderboard and kill feed are drawn by render() after this method returns.
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
        // Switch weapon button (purple)
        shapes.setColor(0.55f, 0.30f, 0.80f, 0.75f);
        shapes.circle(inputHandler.switchBtnX, inputHandler.switchBtnY, inputHandler.switchBtnR, 24);
        shapes.setColor(0.35f, 0.15f, 0.55f, 0.85f);
        shapes.circle(inputHandler.switchBtnX, inputHandler.switchBtnY, inputHandler.switchBtnR - 5f, 24);
        // Reload button (orange)
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

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void rebuildScreenProj() {
        if (screenProj == null) screenProj = new Matrix4();
        screenProj.setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }
}
