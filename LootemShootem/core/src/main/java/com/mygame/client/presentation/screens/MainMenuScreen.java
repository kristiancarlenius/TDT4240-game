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

public final class MainMenuScreen implements Screen {

    private static final int FIELD_W    = 340;
    private static final int FIELD_H    = 38;
    private static final int BTN_W      = 220;
    private static final int BTN_H      = 46;
    private static final int HOW_BTN_W  = 180;
    private static final int HOW_BTN_H  = 40;
    private static final int SET_BTN_W  = 180;
    private static final int SET_BTN_H  = 40;
    private static final int MAX_USERNAME_LEN = 20;
    private static final int MAX_URL_LEN      = 60;

    private final Navigator navigator;
    private String serverUrl;
    private String username;

    private PreferencesPort prefs;
    private int focusedField = 1; // 0 = server URL, 1 = username

    // Rendering resources (created in show())
    private ShapeRenderer shapes;
    private SpriteBatch   batch;
    private BitmapFont    titleFont;
    private BitmapFont    font;
    private GlyphLayout   layout;
    private Matrix4       proj;

    // Cursor blink state
    private float   cursorTimer  = 0f;
    private boolean showCursor   = true;

    private final InputAdapter inputAdapter = new InputAdapter() {
        @Override
        public boolean keyDown(int keycode) {
            if (keycode == Keys.TAB) {
                focusedField = 1 - focusedField;
                return true;
            }
            if (keycode == Keys.BACKSPACE) {
                if (focusedField == 0 && serverUrl.length() > 0) {
                    serverUrl = serverUrl.substring(0, serverUrl.length() - 1);
                } else if (focusedField == 1 && username.length() > 0) {
                    username = username.substring(0, username.length() - 1);
                }
                return true;
            }
            if (keycode == Keys.ENTER || keycode == Keys.NUMPAD_ENTER) {
                tryConnect();
                return true;
            }
            return false;
        }

        @Override
        public boolean keyTyped(char c) {
            if (c < 32 || c >= 127) return false;
            if (focusedField == 0 && serverUrl.length() < MAX_URL_LEN) {
                serverUrl += c;
                return true;
            }
            if (focusedField == 1 && username.length() < MAX_USERNAME_LEN) {
                username += c;
                return true;
            }
            return false;
        }

        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            int sw = Gdx.graphics.getWidth();
            int sh = Gdx.graphics.getHeight();
            int worldY = sh - screenY;

            // URL field
            int urlX = (sw - FIELD_W) / 2;
            int urlY = sh / 2 + 60;
            if (screenX >= urlX && screenX <= urlX + FIELD_W
                    && worldY >= urlY && worldY <= urlY + FIELD_H) {
                focusedField = 0;
                return true;
            }
            // Username field
            int fieldX = (sw - FIELD_W) / 2;
            int fieldY = sh / 2 - 10;
            if (screenX >= fieldX && screenX <= fieldX + FIELD_W
                    && worldY >= fieldY && worldY <= fieldY + FIELD_H) {
                focusedField = 1;
                return true;
            }
            // Connect button
            int btnX = (sw - BTN_W) / 2;
            int btnY = sh / 2 - 90;
            if (screenX >= btnX && screenX <= btnX + BTN_W
                    && worldY >= btnY && worldY <= btnY + BTN_H) {
                tryConnect();
                return true;
            }
            // How to Play button
            int howX = (sw - HOW_BTN_W) / 2;
            int howY = sh / 2 - 160;
            if (screenX >= howX && screenX <= howX + HOW_BTN_W
                    && worldY >= howY && worldY <= howY + HOW_BTN_H) {
                navigator.showTutorial(serverUrl, username);
                return true;
            }
            // Settings button
            int setX = (sw - SET_BTN_W) / 2;
            int setY = sh / 2 - 220;
            if (screenX >= setX && screenX <= setX + SET_BTN_W
                    && worldY >= setY && worldY <= setY + SET_BTN_H) {
                navigator.showSettings(serverUrl, username);
                return true;
            }
            return false;
        }
    };

    public MainMenuScreen(Navigator navigator, String serverUrl, String defaultUsername) {
        this.navigator  = navigator;
        this.serverUrl  = serverUrl != null ? serverUrl : "";
        this.username   = defaultUsername != null ? defaultUsername : "";
    }

    // ---- Screen lifecycle ----

    @Override
    public void show() {
        shapes    = new ShapeRenderer();
        batch     = new SpriteBatch();
        titleFont = new BitmapFont();
        titleFont.getData().setScale(3.2f);
        font = new BitmapFont();
        font.getData().setScale(1.5f);
        layout = new GlyphLayout();
        proj   = new Matrix4();
        rebuildProj();

        prefs = new PreferencesRepository();
        if (username.isEmpty()) username = prefs.getUsername();

        Gdx.input.setInputProcessor(inputAdapter);
    }

    @Override
    public void render(float delta) {
        cursorTimer += delta;
        if (cursorTimer >= 0.5f) {
            cursorTimer = 0f;
            showCursor  = !showCursor;
        }

        int sw = Gdx.graphics.getWidth();
        int sh = Gdx.graphics.getHeight();

        Gdx.gl.glClearColor(0.08f, 0.08f, 0.10f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        int urlX   = (sw - FIELD_W)    / 2;
        int urlY   = sh / 2 + 60;
        int fieldX = (sw - FIELD_W)    / 2;
        int fieldY = sh / 2 - 10;
        int btnX   = (sw - BTN_W)      / 2;
        int btnY   = sh / 2 - 90;
        int howX   = (sw - HOW_BTN_W)  / 2;
        int howY   = sh / 2 - 160;
        int setX   = (sw - SET_BTN_W)  / 2;
        int setY   = sh / 2 - 220;

        // --- Shapes ---
        shapes.setProjectionMatrix(proj);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        // Server URL field background
        shapes.setColor(0.18f, 0.18f, 0.22f, 1f);
        shapes.rect(urlX, urlY, FIELD_W, FIELD_H);
        drawFieldBorder(urlX, urlY, focusedField == 0);

        // Username field background
        shapes.setColor(0.18f, 0.18f, 0.22f, 1f);
        shapes.rect(fieldX, fieldY, FIELD_W, FIELD_H);
        drawFieldBorder(fieldX, fieldY, focusedField == 1);

        // Connect button
        shapes.setColor(0.15f, 0.50f, 0.80f, 1f);
        shapes.rect(btnX, btnY, BTN_W, BTN_H);

        // How to Play button
        shapes.setColor(0.22f, 0.38f, 0.22f, 1f);
        shapes.rect(howX, howY, HOW_BTN_W, HOW_BTN_H);

        // Settings button
        shapes.setColor(0.28f, 0.28f, 0.38f, 1f);
        shapes.rect(setX, setY, SET_BTN_W, SET_BTN_H);

        shapes.end();

        // --- Text ---
        batch.setProjectionMatrix(proj);
        batch.begin();

        // Title
        titleFont.setColor(new Color(1f, 0.82f, 0.15f, 1f));
        String title = "SHOOT 'EM N LOOT 'EM";
        layout.setText(titleFont, title);
        titleFont.draw(batch, title, (sw - layout.width) / 2f, sh * 0.76f);

        // Server URL label + content
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch, "Server URL", urlX, urlY + FIELD_H + 24f);
        font.setColor(Color.WHITE);
        String urlDisplay = serverUrl + (focusedField == 0 && showCursor ? "|" : "");
        font.draw(batch, urlDisplay, urlX + 10f, urlY + FIELD_H - 6f);

        // Username label + content
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch, "Username", fieldX, fieldY + FIELD_H + 24f);
        font.setColor(Color.WHITE);
        String nameDisplay = username + (focusedField == 1 && showCursor ? "|" : "");
        font.draw(batch, nameDisplay, fieldX + 10f, fieldY + FIELD_H - 6f);

        // Button label
        font.setColor(Color.WHITE);
        String btnLabel = "CONNECT";
        layout.setText(font, btnLabel);
        font.draw(batch, btnLabel, btnX + (BTN_W - layout.width) / 2f, btnY + BTN_H - 10f);

        // How to Play button label
        font.setColor(new Color(0.55f, 0.90f, 0.55f, 1f));
        String howLabel = "HOW TO PLAY";
        layout.setText(font, howLabel);
        font.draw(batch, howLabel, howX + (HOW_BTN_W - layout.width) / 2f, howY + HOW_BTN_H - 10f);

        // Settings button label
        font.setColor(new Color(0.70f, 0.70f, 0.90f, 1f));
        String setLabel = "SETTINGS";
        layout.setText(font, setLabel);
        font.draw(batch, setLabel, setX + (SET_BTN_W - layout.width) / 2f, setY + SET_BTN_H - 10f);

        // Hint
        font.setColor(0.45f, 0.45f, 0.50f, 1f);
        font.getData().setScale(1.1f);
        String hint = "TAB to switch fields  •  ENTER to connect";
        layout.setText(font, hint);
        font.draw(batch, hint, (sw - layout.width) / 2f, btnY - 18f);
        font.getData().setScale(1.5f);

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

    // ---- Helpers ----

    /** Draws a border around a field. shapes must be in Filled begin/end block. */
    private void drawFieldBorder(int x, int y, boolean focused) {
        shapes.setColor(focused ? 0.45f : 0.28f,
                        focused ? 0.65f : 0.28f,
                        focused ? 0.95f : 0.35f, 1f);
        shapes.rectLine(x,          y,          x + FIELD_W, y,          2f);
        shapes.rectLine(x,          y + FIELD_H, x + FIELD_W, y + FIELD_H, 2f);
        shapes.rectLine(x,          y,          x,           y + FIELD_H, 2f);
        shapes.rectLine(x + FIELD_W, y,          x + FIELD_W, y + FIELD_H, 2f);
    }

    private void rebuildProj() {
        if (proj == null) proj = new Matrix4();
        proj.setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    private void tryConnect() {
        String url  = serverUrl.trim();
        String name = username.trim();
        if (!url.isEmpty() && !name.isEmpty()) {
            prefs.saveUsername(name);
            navigator.showGame(url, name);
        }
    }
}
