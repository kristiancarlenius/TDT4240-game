package com.mygame.client.presentation.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.mygame.client.data.repository.PreferencesRepository;
import com.mygame.client.domain.model.HudSlot;
import com.mygame.client.domain.model.HudWidget;
import com.mygame.client.domain.ports.PreferencesPort;
import com.mygame.client.presentation.navigation.Navigator;

/**
 * Settings screen — lets the player:
 *   • Toggle sound on/off
 *   • Toggle control layout (default / inverted)
 *   • Reassign the three movable HUD widgets
 *     (Kill Feed, Time Alive, Leaderboard) to the three anchor slots
 *     (Top-Left, Top-Center, Top-Right).
 *
 * Tapping the widget name in any row cycles it to the next widget.
 * If that widget is already assigned to another slot the two slots swap,
 * so every widget is always visible in exactly one slot.
 */
public final class SettingsScreen implements Screen {

    private static final int   BTN_W   = 220;
    private static final int   BTN_H   = 46;
    private static final int   ROW_W   = 380;
    private static final int   ROW_H   = 46;
    private static final float ROW_GAP = 75f;

    private final Navigator navigator;
    private final String    serverUrl;
    private final String    username;

    private PreferencesPort prefs;

    private ShapeRenderer shapes;
    private SpriteBatch   batch;
    private BitmapFont    titleFont;
    private BitmapFont    font;
    private GlyphLayout   layout;
    private Matrix4       proj;

    // Cached slot assignments (kept in sync with prefs)
    private final HudWidget[] assignments = new HudWidget[3]; // indexed by HudSlot.ordinal()

    private final InputAdapter inputAdapter = new InputAdapter() {
        @Override
        public boolean keyDown(int keycode) {
            if (keycode == Keys.ESCAPE || keycode == Keys.BACK) {
                navigator.showMainMenu(serverUrl, username);
                return true;
            }
            return false;
        }

        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            int sw = Gdx.graphics.getWidth();
            int sh = Gdx.graphics.getHeight();
            int worldY = sh - screenY;

            // Toggle sound button
            int toggleW = 360, toggleH = 50;
            int toggleX = (sw - toggleW) / 2;
            int toggleY = (int)(sh * 0.74f);
            if (screenX >= toggleX && screenX <= toggleX + toggleW
                    && worldY >= toggleY && worldY <= toggleY + toggleH) {
                prefs.setSoundEnabled(!prefs.isSoundEnabled());
                return true;
            }

            // Toggle controls button
            int ctrlW = 360, ctrlH = 50;
            int ctrlX = (sw - ctrlW) / 2;
            int ctrlY = (int)(sh * 0.62f);
            if (screenX >= ctrlX && screenX <= ctrlX + ctrlW
                    && worldY >= ctrlY && worldY <= ctrlY + ctrlH) {
                prefs.setControlsSwapped(!prefs.isControlsSwapped());
                return true;
            }

            // Check each slot row's widget button
            for (HudSlot slot : HudSlot.values()) {
                int rowY = rowY(slot, sh);
                int rowX = (sw - ROW_W) / 2 + 160; // widget column x
                int wBtnW = ROW_W - 160;
                if (screenX >= rowX && screenX <= rowX + wBtnW
                        && worldY >= rowY && worldY <= rowY + ROW_H) {
                    cycleSlot(slot);
                    return true;
                }
            }

            // Back button
            int btnX = (sw - BTN_W) / 2;
            int btnY = backBtnY(sh);
            if (screenX >= btnX && screenX <= btnX + BTN_W
                    && worldY >= btnY && worldY <= btnY + BTN_H) {
                navigator.showMainMenu(serverUrl, username);
                return true;
            }
            return false;
        }
    };

    public SettingsScreen(Navigator navigator, String serverUrl, String username) {
        this.navigator = navigator;
        this.serverUrl = serverUrl;
        this.username  = username;
    }

    // ── Screen lifecycle ─────────────────────────────────────────────────────

    @Override
    public void show() {
        shapes    = new ShapeRenderer();
        batch     = new SpriteBatch();
        titleFont = new BitmapFont();
        titleFont.getData().setScale(2.4f);
        font = new BitmapFont();
        font.getData().setScale(1.5f);
        layout = new GlyphLayout();
        proj   = new Matrix4();
        rebuildProj();

        prefs = new PreferencesRepository();
        for (HudSlot slot : HudSlot.values()) {
            assignments[slot.ordinal()] = prefs.getHudWidget(slot);
        }

        Gdx.input.setInputProcessor(inputAdapter);
    }

    @Override
    public void render(float delta) {
        int sw = Gdx.graphics.getWidth();
        int sh = Gdx.graphics.getHeight();

        Gdx.gl.glClearColor(0.08f, 0.08f, 0.10f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        shapes.setProjectionMatrix(proj);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        // Toggle sound button background
        int toggleW = 360, toggleH = 50;
        int toggleX = (sw - toggleW) / 2;
        int toggleY = (int)(sh * 0.74f);
        shapes.setColor(prefs.isSoundEnabled() ? 0.2f : 0.4f,
                        prefs.isSoundEnabled() ? 0.6f : 0.2f,
                        0.2f, 1f);
        shapes.rect(toggleX, toggleY, toggleW, toggleH);

        // Toggle controls button background
        int ctrlW = 360, ctrlH = 50;
        int ctrlX = (sw - ctrlW) / 2;
        int ctrlY = (int)(sh * 0.62f);
        shapes.setColor(0.3f, 0.4f, 0.5f, 1f);
        shapes.rect(ctrlX, ctrlY, ctrlW, ctrlH);

        // Row backgrounds (HUD slot assignments)
        for (HudSlot slot : HudSlot.values()) {
            int rowY = rowY(slot, sh);
            int rowX = (sw - ROW_W) / 2;
            shapes.setColor(0.15f, 0.15f, 0.18f, 1f);
            shapes.rect(rowX, rowY, ROW_W, ROW_H);
            // Widget button highlight
            shapes.setColor(0.22f, 0.30f, 0.45f, 1f);
            shapes.rect(rowX + 160, rowY, ROW_W - 160, ROW_H);
        }

        // Back button
        shapes.setColor(0.22f, 0.22f, 0.28f, 1f);
        shapes.rect((sw - BTN_W) / 2, backBtnY(sh), BTN_W, BTN_H);

        shapes.end();

        batch.setProjectionMatrix(proj);
        batch.begin();

        // Title
        titleFont.setColor(new Color(1f, 0.82f, 0.15f, 1f));
        String title = "SETTINGS";
        layout.setText(titleFont, title);
        titleFont.draw(batch, title, (sw - layout.width) / 2f, sh * 0.92f);

        // Sound toggle label
        font.setColor(Color.WHITE);
        String toggleLabel = "SOUND: " + (prefs.isSoundEnabled() ? "ON" : "OFF");
        layout.setText(font, toggleLabel);
        font.draw(batch, toggleLabel, toggleX + (toggleW - layout.width) / 2f, toggleY + toggleH - 15f);

        // Controls toggle label
        String ctrlLabel = "CONTROL LAYOUT: " + (prefs.isControlsSwapped() ? "INVERTED" : "DEFAULT");
        layout.setText(font, ctrlLabel);
        font.draw(batch, ctrlLabel, ctrlX + (ctrlW - layout.width) / 2f, ctrlY + ctrlH - 15f);

        // HUD layout sub-header
        font.setColor(new Color(0.55f, 0.55f, 0.60f, 1f));
        String sub = "HUD LAYOUT  •  tap widget to cycle";
        layout.setText(font, sub);
        font.getData().setScale(1.1f);
        font.draw(batch, sub, (sw - layout.width) / 2f, sh * 0.535f);
        font.getData().setScale(1.5f);

        // Slot rows
        for (HudSlot slot : HudSlot.values()) {
            int rowY = rowY(slot, sh);
            int rowX = (sw - ROW_W) / 2;

            // Slot label (left column)
            font.setColor(Color.LIGHT_GRAY);
            font.draw(batch, slotLabel(slot), rowX + 10f, rowY + ROW_H - 12f);

            // Widget name (right column, centered in button)
            String widgetName = widgetLabel(assignments[slot.ordinal()]);
            font.setColor(Color.WHITE);
            layout.setText(font, widgetName);
            font.draw(batch, widgetName,
                    rowX + 160 + (ROW_W - 160 - layout.width) / 2f,
                    rowY + ROW_H - 12f);
        }

        // Back button
        font.setColor(Color.WHITE);
        String backLabel = "BACK";
        layout.setText(font, backLabel);
        font.draw(batch, backLabel,
                (sw - layout.width) / 2f,
                backBtnY(sh) + BTN_H - 10f);

        batch.end();
    }

    @Override
    public void resize(int width, int height) {
        rebuildProj();
    }

    @Override public void pause()  {}
    @Override public void resume() {}

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
    }

    @Override
    public void dispose() {
        if (shapes    != null) shapes.dispose();
        if (batch     != null) batch.dispose();
        if (titleFont != null) titleFont.dispose();
        if (font      != null) font.dispose();
    }

    // ── Logic ────────────────────────────────────────────────────────────────

    /**
     * Cycles the widget in {@code slot} to the next value.
     * If another slot already has that widget they swap.
     */
    private void cycleSlot(HudSlot slot) {
        HudWidget current = assignments[slot.ordinal()];
        HudWidget[] all   = HudWidget.values();
        HudWidget next    = all[(current.ordinal() + 1) % all.length];

        // Find if next is already assigned somewhere else
        HudSlot conflict = null;
        for (HudSlot other : HudSlot.values()) {
            if (other != slot && assignments[other.ordinal()] == next) {
                conflict = other;
                break;
            }
        }

        assignments[slot.ordinal()] = next;
        if (conflict != null) {
            // Swap: give the conflicting slot our old widget
            assignments[conflict.ordinal()] = current;
            prefs.saveHudWidget(conflict, current);
        }
        prefs.saveHudWidget(slot, next);
    }

    // ── Positioning helpers ───────────────────────────────────────────────────

    private int rowY(HudSlot slot, int sh) {
        switch (slot) {
            case TOP_LEFT:   return (int)(sh * 0.44f);
            case TOP_CENTER: return (int)(sh * 0.33f);
            case TOP_RIGHT:  return (int)(sh * 0.22f);
            default:         return (int)(sh * 0.33f);
        }
    }

    private int backBtnY(int sh) {
        return (int)(sh * 0.08f);
    }

    private static String slotLabel(HudSlot slot) {
        switch (slot) {
            case TOP_LEFT:   return "TOP  LEFT";
            case TOP_CENTER: return "TOP  CENTER";
            case TOP_RIGHT:  return "TOP  RIGHT";
            default:         return slot.name();
        }
    }

    private static String widgetLabel(HudWidget widget) {
        switch (widget) {
            case KILL_FEED:   return "KILL FEED";
            case TIME_ALIVE:  return "TIME ALIVE";
            case LEADERBOARD: return "LEADERBOARD";
            default:          return widget.name();
        }
    }

    private void rebuildProj() {
        if (proj == null) proj = new Matrix4();
        proj.setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }
}
