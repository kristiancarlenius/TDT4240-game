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

/**
 * Renders all screen-space HUD elements: health bar, weapon info, kill score,
 * leaderboard, speed-boost timer, death overlay, and touch controls on Android.
 * Reads from WorldState and InputHandler; never mutates either.
 */
public final class HudRenderer {

    private final WorldState   worldState;
    private final InputHandler inputHandler;
    private final ShapeRenderer shapes;
    private final SpriteBatch  batch;
    private final BitmapFont   font;
    private final BitmapFont   bigFont;
    private final GlyphLayout  layout = new GlyphLayout();
    private       Matrix4      screenProj;

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

    /** Call from Screen.resize() to keep the projection matrix up to date. */
    public void resize() {
        rebuildScreenProj();
    }

    /** Render the full HUD for this frame. */
    public void render() {
        GameSnapshotDto snap = worldState.getSnapshot();
        PlayerDto       me   = worldState.getLocalPlayer();

        if (snap == null || me == null) {
            drawConnecting();
            return;
        }

        if (me.isDead) {
            drawDeathOverlay(me);
        } else {
            drawHealthBar(me);
            drawPlayerInfo(me, snap);
        }

        if (inputHandler.isAndroid()) drawTouchControls();
    }

    // ---- HUD panels ----

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

    private void drawPlayerInfo(PlayerDto me, GameSnapshotDto snap) {
        int sw = Gdx.graphics.getWidth();
        int sh = Gdx.graphics.getHeight();
        int barY = 20, barH = 18;

        batch.setProjectionMatrix(screenProj);
        batch.begin();

        font.setColor(Color.WHITE);
        font.draw(batch, "HP  " + (int) me.hp + " / 100", 20, barY + barH + 18f);

        String weaponLine = me.equippedWeaponType != null
                ? me.equippedWeaponType.name() + "   " + me.equippedAmmo + " ammo" : "—";
        font.draw(batch, weaponLine, 20, barY + barH + 36f);

        if (me.speedBoostTimer > 0f) {
            font.setColor(new Color(0.20f, 0.85f, 0.95f, 1f));
            font.draw(batch, "SPEED BOOST  " + (int) Math.ceil(me.speedBoostTimer) + "s",
                    20, barY + barH + 56f);
        }

        // Kill score (top-right)
        font.setColor(new Color(1f, 0.85f, 0.2f, 1f));
        String scoreText = "Kills: " + me.score;
        layout.setText(font, scoreText);
        font.draw(batch, scoreText, sw - layout.width - 20f, sh - 20f);

        // Leaderboard (other players)
        font.setColor(Color.LIGHT_GRAY);
        float lbY = sh - 50f;
        String localId = worldState.getLocalPlayerId();
        for (PlayerDto p : snap.players) {
            if (localId != null && localId.equals(p.playerId)) continue;
            font.draw(batch, p.username + ": " + p.score + " kills", sw - 200f, lbY);
            lbY -= 22f;
        }

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
        shapes.end();

        batch.setProjectionMatrix(screenProj);
        batch.begin();
        font.setColor(Color.WHITE);
        layout.setText(font, "SW");
        font.draw(batch, "SW",
                inputHandler.switchBtnX - layout.width / 2f,
                inputHandler.switchBtnY + layout.height / 2f);
        batch.end();
    }

    private void rebuildScreenProj() {
        if (screenProj == null) screenProj = new Matrix4();
        screenProj.setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }
}
