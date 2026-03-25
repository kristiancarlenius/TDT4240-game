package com.mygame.client.presentation.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.InputAdapter;
import com.mygame.client.data.repository.PreferencesRepository;
import com.mygame.client.domain.ports.PreferencesPort;
import com.mygame.client.presentation.navigation.Navigator;

public final class SettingsScreen implements Screen {

    private final Navigator navigator;
    private final String serverUrl;
    private final String username;
    private final PreferencesPort prefs;

    private ShapeRenderer shapes;
    private SpriteBatch   batch;
    private BitmapFont    titleFont;
    private BitmapFont    font;
    private GlyphLayout   layout;
    private Matrix4       proj;

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
            int toggleW = 300, toggleH = 50;
            int toggleX = (sw - toggleW) / 2;
            int toggleY = sh / 2 + 60;
            if (screenX >= toggleX && screenX <= toggleX + toggleW
                    && worldY >= toggleY && worldY <= toggleY + toggleH) {
                prefs.setSoundEnabled(!prefs.isSoundEnabled());
                return true;
            }

            // Toggle controls button
            int ctrlW = 300, ctrlH = 50;
            int ctrlX = (sw - ctrlW) / 2;
            int ctrlY = sh / 2;
            if (screenX >= ctrlX && screenX <= ctrlX + ctrlW
                    && worldY >= ctrlY && worldY <= ctrlY + ctrlH) {
                prefs.setControlsSwapped(!prefs.isControlsSwapped());
                return true;
            }

            // Back button
            int btnW = 200, btnH = 50;
            int btnX = (sw - btnW) / 2;
            int btnY = 100;
            if (screenX >= btnX && screenX <= btnX + btnW
                    && worldY >= btnY && worldY <= btnY + btnH) {
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
        this.prefs     = new PreferencesRepository();
    }

    @Override
    public void show() {
        shapes    = new ShapeRenderer();
        batch     = new SpriteBatch();
        titleFont = new BitmapFont();
        titleFont.getData().setScale(3f);
        font = new BitmapFont();
        font.getData().setScale(1.5f);
        layout = new GlyphLayout();
        proj   = new Matrix4();
        rebuildProj();
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
        int toggleW = 300, toggleH = 50;
        int toggleX = (sw - toggleW) / 2;
        int toggleY = sh / 2 + 60;
        shapes.setColor(prefs.isSoundEnabled() ? 0.2f : 0.4f, 
                        prefs.isSoundEnabled() ? 0.6f : 0.2f, 
                        0.2f, 1f);
        shapes.rect(toggleX, toggleY, toggleW, toggleH);

        // Toggle controls button background
        int ctrlW = 300, ctrlH = 50;
        int ctrlX = (sw - ctrlW) / 2;
        int ctrlY = sh / 2;
        shapes.setColor(0.3f, 0.4f, 0.5f, 1f);
        shapes.rect(ctrlX, ctrlY, ctrlW, ctrlH);

        // Back button background
        int btnW = 200, btnH = 50;
        int btnX = (sw - btnW) / 2;
        int btnY = 100;
        shapes.setColor(0.3f, 0.3f, 0.35f, 1f);
        shapes.rect(btnX, btnY, btnW, btnH);
        
        shapes.end();

        batch.setProjectionMatrix(proj);
        batch.begin();

        titleFont.setColor(Color.WHITE);
        String title = "SETTINGS";
        layout.setText(titleFont, title);
        titleFont.draw(batch, title, (sw - layout.width) / 2f, sh * 0.8f);

        font.setColor(Color.WHITE);
        String toggleLabel = "SOUND: " + (prefs.isSoundEnabled() ? "ON" : "OFF");
        layout.setText(font, toggleLabel);
        font.draw(batch, toggleLabel, toggleX + (toggleW - layout.width) / 2f, toggleY + toggleH - 15f);

        String ctrlLabel = "CONTROL LAYOUT: " + (prefs.isControlsSwapped() ? "INVERTED" : "DEFAULT");
        layout.setText(font, ctrlLabel);
        font.draw(batch, ctrlLabel, ctrlX + (ctrlW - layout.width) / 2f, ctrlY + ctrlH - 15f);

        String btnLabel = "BACK";
        layout.setText(font, btnLabel);
        font.draw(batch, btnLabel, btnX + (btnW - layout.width) / 2f, btnY + btnH - 15f);

        batch.end();
    }

    @Override
    public void resize(int width, int height) {
        rebuildProj();
    }

    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() { Gdx.input.setInputProcessor(null); }
    @Override public void dispose() {
        if (shapes != null) shapes.dispose();
        if (batch != null) batch.dispose();
        if (titleFont != null) titleFont.dispose();
        if (font != null) font.dispose();
    }

    private void rebuildProj() {
        if (proj == null) proj = new Matrix4();
        proj.setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }
}
