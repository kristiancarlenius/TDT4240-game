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
 *   • Toggle music on/off
 *   • Toggle control layout (default / inverted)
 *   • Reassign the three movable HUD widgets
 */
public final class SettingsScreen implements Screen {

    private static final int   BTN_W   = 220;
    private static final int   BTN_H   = 46;
    private static final int   ROW_W   = 380;
    private static final int   ROW_H   = 46;

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

    private final HudWidget[] assignments = new HudWidget[3];

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

            int btnW = 360, btnH = 48;
            int btnX = (sw - btnW) / 2;

            // Toggle sound button
            int soundY = (int)(sh * 0.76f);
            if (screenX >= btnX && screenX <= btnX + btnW && worldY >= soundY && worldY <= soundY + btnH) {
                prefs.setSoundEnabled(!prefs.isSoundEnabled());
                return true;
            }

            // Toggle music button
            int musicY = (int)(sh * 0.68f);
            if (screenX >= btnX && screenX <= btnX + btnW && worldY >= musicY && worldY <= musicY + btnH) {
                prefs.setMusicEnabled(!prefs.isMusicEnabled());
                return true;
            }

            // Toggle controls button
            int ctrlY = (int)(sh * 0.60f);
            if (screenX >= btnX && screenX <= btnX + btnW && worldY >= ctrlY && worldY <= ctrlY + btnH) {
                prefs.setControlsSwapped(!prefs.isControlsSwapped());
                return true;
            }

            // Check each slot row's widget button
            for (HudSlot slot : HudSlot.values()) {
                int rowY = rowY(slot, sh);
                int rowX = (sw - ROW_W) / 2 + 160;
                int wBtnW = ROW_W - 160;
                if (screenX >= rowX && screenX <= rowX + wBtnW && worldY >= rowY && worldY <= rowY + ROW_H) {
                    cycleSlot(slot);
                    return true;
                }
            }

            // Back button
            int backX = (sw - BTN_W) / 2;
            int backY = backBtnY(sh);
            if (screenX >= backX && screenX <= backX + BTN_W && worldY >= backY && worldY <= backY + BTN_H) {
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

        int btnW = 360, btnH = 48;
        int btnX = (sw - btnW) / 2;

        // Sound button
        int soundY = (int)(sh * 0.76f);
        shapes.setColor(prefs.isSoundEnabled() ? 0.2f : 0.4f, prefs.isSoundEnabled() ? 0.6f : 0.2f, 0.2f, 1f);
        shapes.rect(btnX, soundY, btnW, btnH);

        // Music button
        int musicY = (int)(sh * 0.68f);
        shapes.setColor(prefs.isMusicEnabled() ? 0.2f : 0.4f, prefs.isMusicEnabled() ? 0.6f : 0.2f, 0.2f, 1f);
        shapes.rect(btnX, musicY, btnW, btnH);

        // Controls button
        int ctrlY = (int)(sh * 0.60f);
        shapes.setColor(0.3f, 0.4f, 0.5f, 1f);
        shapes.rect(btnX, ctrlY, btnW, btnH);

        // HUD rows
        for (HudSlot slot : HudSlot.values()) {
            int rowY = rowY(slot, sh);
            int rowX = (sw - ROW_W) / 2;
            shapes.setColor(0.15f, 0.15f, 0.18f, 1f);
            shapes.rect(rowX, rowY, ROW_W, ROW_H);
            shapes.setColor(0.22f, 0.30f, 0.45f, 1f);
            shapes.rect(rowX + 160, rowY, ROW_W - 160, ROW_H);
        }

        // Back button
        shapes.setColor(0.22f, 0.22f, 0.28f, 1f);
        shapes.rect((sw - BTN_W) / 2, backBtnY(sh), BTN_W, BTN_H);

        shapes.end();

        batch.setProjectionMatrix(proj);
        batch.begin();

        titleFont.setColor(new Color(1f, 0.82f, 0.15f, 1f));
        String title = "SETTINGS";
        layout.setText(titleFont, title);
        titleFont.draw(batch, title, (sw - layout.width) / 2f, sh * 0.92f);

        font.setColor(Color.WHITE);
        String soundLabel = "SOUND FX: " + (prefs.isSoundEnabled() ? "ON" : "OFF");
        layout.setText(font, soundLabel);
        font.draw(batch, soundLabel, btnX + (btnW - layout.width) / 2f, soundY + btnH - 14f);

        String musicLabel = "MUSIC: " + (prefs.isMusicEnabled() ? "ON" : "OFF");
        layout.setText(font, musicLabel);
        font.draw(batch, musicLabel, btnX + (btnW - layout.width) / 2f, musicY + btnH - 14f);

        String ctrlLabel = "CONTROL LAYOUT: " + (prefs.isControlsSwapped() ? "INVERTED" : "DEFAULT");
        layout.setText(font, ctrlLabel);
        font.draw(batch, ctrlLabel, btnX + (btnW - layout.width) / 2f, ctrlY + btnH - 14f);

        font.setColor(new Color(0.55f, 0.55f, 0.60f, 1f));
        font.getData().setScale(1.1f);
        String sub = "HUD LAYOUT  •  tap widget to cycle";
        layout.setText(font, sub);
        font.draw(batch, sub, (sw - layout.width) / 2f, sh * 0.535f);
        font.getData().setScale(1.5f);

        for (HudSlot slot : HudSlot.values()) {
            int rowY = rowY(slot, sh);
            int rowX = (sw - ROW_W) / 2;
            font.setColor(Color.LIGHT_GRAY);
            font.draw(batch, slotLabel(slot), rowX + 10f, rowY + ROW_H - 12f);
            String widgetName = widgetLabel(assignments[slot.ordinal()]);
            font.setColor(Color.WHITE);
            layout.setText(font, widgetName);
            font.draw(batch, widgetName, rowX + 160 + (ROW_W - 160 - layout.width) / 2f, rowY + ROW_H - 12f);
        }

        font.setColor(Color.WHITE);
        String backLabel = "BACK";
        layout.setText(font, backLabel);
        font.draw(batch, backLabel, (sw - layout.width) / 2f, backBtnY(sh) + BTN_H - 10f);

        batch.end();
    }

    @Override public void resize(int width, int height) { rebuildProj(); }
    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() { Gdx.input.setInputProcessor(null); }

    @Override
    public void dispose() {
        if (shapes    != null) shapes.dispose();
        if (batch     != null) batch.dispose();
        if (titleFont != null) titleFont.dispose();
        if (font      != null) font.dispose();
    }

    private void cycleSlot(HudSlot slot) {
        HudWidget current = assignments[slot.ordinal()];
        HudWidget[] all   = HudWidget.values();
        HudWidget next    = all[(current.ordinal() + 1) % all.length];
        HudSlot conflict = null;
        for (HudSlot other : HudSlot.values()) {
            if (other != slot && assignments[other.ordinal()] == next) {
                conflict = other;
                break;
            }
        }
        assignments[slot.ordinal()] = next;
        if (conflict != null) {
            assignments[conflict.ordinal()] = current;
            prefs.saveHudWidget(conflict, current);
        }
        prefs.saveHudWidget(slot, next);
    }

    private int rowY(HudSlot slot, int sh) {
        switch (slot) {
            case TOP_LEFT:   return (int)(sh * 0.44f);
            case TOP_CENTER: return (int)(sh * 0.33f);
            case TOP_RIGHT:  return (int)(sh * 0.22f);
            default:         return (int)(sh * 0.33f);
        }
    }

    private int backBtnY(int sh) { return (int)(sh * 0.08f); }

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
            case MINIMAP:     return "MINIMAP";
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
