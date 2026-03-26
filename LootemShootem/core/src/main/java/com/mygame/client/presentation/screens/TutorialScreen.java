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
import com.mygame.client.presentation.navigation.Navigator;

public final class TutorialScreen implements Screen {

    private static final int BTN_W = 180;
    private static final int BTN_H = 44;

    private final Navigator navigator;
    private final String    serverUrl;
    private final String    username;

    private ShapeRenderer shapes;
    private SpriteBatch   batch;
    private BitmapFont    titleFont;
    private BitmapFont    font;
    private GlyphLayout   layout;
    private Matrix4       proj;

    public TutorialScreen(Navigator navigator, String serverUrl, String username) {
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
        font.getData().setScale(1.4f);
        layout = new GlyphLayout();
        proj   = new Matrix4();
        rebuildProj();

        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Keys.ESCAPE || keycode == Keys.BACK) {
                    navigator.showMainMenu(serverUrl, username);
                    return true;
                }
                return false;
            }

            @Override
            public boolean touchDown(int sx, int sy, int pointer, int button) {
                int sw = Gdx.graphics.getWidth();
                int sh = Gdx.graphics.getHeight();
                int btnX = (sw - BTN_W) / 2;
                int btnY = 40;
                int worldY = sh - sy;
                if (sx >= btnX && sx <= btnX + BTN_W && worldY >= btnY && worldY <= btnY + BTN_H) {
                    navigator.showMainMenu(serverUrl, username);
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    public void render(float delta) {
        int sw = Gdx.graphics.getWidth();
        int sh = Gdx.graphics.getHeight();

        Gdx.gl.glClearColor(0.08f, 0.08f, 0.10f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        int btnX = (sw - BTN_W) / 2;
        int btnY = 40;

        shapes.setProjectionMatrix(proj);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.15f, 0.50f, 0.80f, 1f);
        shapes.rect(btnX, btnY, BTN_W, BTN_H);
        shapes.end();

        batch.setProjectionMatrix(proj);
        batch.begin();

        titleFont.setColor(new Color(1f, 0.82f, 0.15f, 1f));
        String title = "HOW TO PLAY";
        layout.setText(titleFont, title);
        titleFont.draw(batch, title, (sw - layout.width) / 2f, sh - 60f);

        float col1 = sw * 0.12f;
        float col2 = sw * 0.52f;
        float y    = sh - 140f;
        float gap  = 34f;

        font.setColor(new Color(0.6f, 0.8f, 1f, 1f));
        font.draw(batch, "DESKTOP",    col1, y);
        font.draw(batch, "ANDROID",    col2, y);
        y -= gap;

        String[][] rows = {
            { "WASD",         "Left joystick",   "Move" },
            { "Mouse + LMB",  "Right joystick",  "Aim & shoot" },
            { "Space",        "SW button",        "Switch weapon" },
            { "Escape",       "Back",             "Return to menu" },
        };

        for (String[] row : rows) {
            font.setColor(Color.WHITE);
            font.draw(batch, row[0], col1, y);
            font.draw(batch, row[1], col2, y);
            font.setColor(Color.LIGHT_GRAY);
            float descX = sw * 0.75f;
            font.draw(batch, row[2], descX, y);
            y -= gap;
        }

        y -= gap / 2f;
        font.setColor(new Color(0.6f, 0.8f, 1f, 1f));
        font.draw(batch, "PICKUPS", col1, y);
        y -= gap;

        String[][] pickupRows = {
            { "Green circle",  "Health restore" },
            { "Cyan circle",   "Speed boost (5 s)" },
            { "Orange circle", "New weapon (fills slot 2)" },
        };

        for (String[] row : pickupRows) {
            font.setColor(Color.LIGHT_GRAY);
            font.draw(batch, row[0] + "  —  " + row[1], col1, y);
            y -= gap;
        }

        y -= gap / 2f;
        font.setColor(new Color(0.6f, 0.8f, 1f, 1f));
        font.draw(batch, "TIPS", col1, y);
        y -= gap;
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch, "You carry up to 2 weapons. Switch between them anytime.", col1, y);
        y -= gap;
        font.draw(batch, "Dying drops your secondary weapon — someone else will grab it.", col1, y);
        y -= gap;
        font.draw(batch, "Health slowly regenerates while alive.", col1, y);

        font.setColor(Color.WHITE);
        layout.setText(font, "BACK");
        font.draw(batch, "BACK", btnX + (BTN_W - layout.width) / 2f, btnY + BTN_H - 10f);

        batch.end();
    }

    @Override public void resize(int w, int h) { rebuildProj(); }
    @Override public void pause()  {}
    @Override public void resume() {}
    @Override public void hide()   { Gdx.input.setInputProcessor(null); }
    @Override public void dispose() {
        if (shapes    != null) shapes.dispose();
        if (batch     != null) batch.dispose();
        if (titleFont != null) titleFont.dispose();
        if (font      != null) font.dispose();
    }

    private void rebuildProj() {
        if (proj == null) proj = new Matrix4();
        proj.setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }
}
