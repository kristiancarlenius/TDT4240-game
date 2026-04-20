package com.mygame.client.presentation.screens;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
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

    private static final int FIELD_W = 340;
    private static final int FIELD_H = 38;
    private static final int BTN_W = 220;
    private static final int BTN_H = 46;
    private static final int HOW_BTN_W = 180;
    private static final int HOW_BTN_H = 40;
    private static final int SET_BTN_W = 180;
    private static final int SET_BTN_H = 40;
    private static final int MAX_USERNAME_LEN = 20;
    private static final int MAX_URL_LEN = 60;

    private static final int SKIN_COUNT = 4;
    private static final int SWATCH_SIZE = 52;
    private static final int SWATCH_GAP = 10;
    private static final int FRAME_PX = 64;

    private static final String[] SKIN_NAMES = { "CRIMSON", "COBALT", "JUNGLE", "VIOLET" };
    private static final float[][] SKIN_RGB = {
            { 0.74f, 0.18f, 0.18f },
            { 0.16f, 0.30f, 0.76f },
            { 0.14f, 0.61f, 0.22f },
            { 0.49f, 0.14f, 0.76f },
    };

    private final Navigator navigator;
    private String serverUrl;
    private String username;
    private int selectedSkin = 0;

    private PreferencesPort prefs;
    private int focusedField = 1;

    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private BitmapFont titleFont;
    private BitmapFont font;
    private GlyphLayout layout;
    private Matrix4 proj;

    private final Texture[] skinPreviews = new Texture[SKIN_COUNT];

    private float cursorTimer = 0f;
    private boolean showCursor = true;

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
            int fieldW = fieldWidth();
            int fieldH = fieldHeight();

            int urlX = (sw - fieldW) / 2;
            int urlY = urlFieldY(sh);
            if (hit(screenX, worldY, urlX, urlY, fieldW, fieldH)) {
                focusedField = 0;
                showSoftKeyboardIfNeeded();
                return true;
            }

            int fieldX = (sw - fieldW) / 2;
            int fieldY = usernameFieldY(sh);
            if (hit(screenX, worldY, fieldX, fieldY, fieldW, fieldH)) {
                focusedField = 1;
                showSoftKeyboardIfNeeded();
                return true;
            }

            int btnW = connectButtonWidth();
            int btnH = connectButtonHeight();
            int btnX = (sw - btnW) / 2;
            int btnY = connectButtonY(sh);
            if (hit(screenX, worldY, btnX, btnY, btnW, btnH)) {
                hideSoftKeyboardIfNeeded();
                tryConnect();
                return true;
            }

            int swatchRowY = skinRowY(sh);
            int swatchStartX = skinStartX(sw);
            int swatchSize = swatchSize();
            int swatchGap = swatchGap();
            for (int i = 0; i < SKIN_COUNT; i++) {
                int sx = swatchStartX + i * (swatchSize + swatchGap);
                if (hit(screenX, worldY, sx, swatchRowY, swatchSize, swatchSize)) {
                    selectedSkin = i;
                    prefs.setSkinId(i);
                    return true;
                }
            }

            int auxW = secondaryButtonWidth();
            int auxH = secondaryButtonHeight();
            int howX = (sw - auxW) / 2;
            int howY = howBtnY(sh);
            if (hit(screenX, worldY, howX, howY, auxW, auxH)) {
                hideSoftKeyboardIfNeeded();
                navigator.showTutorial(serverUrl, username);
                return true;
            }

            int setX = (sw - auxW) / 2;
            int setY = setBtnY(sh);
            if (hit(screenX, worldY, setX, setY, auxW, auxH)) {
                hideSoftKeyboardIfNeeded();
                navigator.showSettings(serverUrl, username);
                return true;
            }
            return false;
        }
    };

    public MainMenuScreen(Navigator navigator, String serverUrl, String defaultUsername) {
        this.navigator = navigator;
        this.serverUrl = serverUrl != null ? serverUrl : "";
        this.username = defaultUsername != null ? defaultUsername : "";
    }

    @Override
    public void show() {
        shapes = new ShapeRenderer();
        batch = new SpriteBatch();
        titleFont = new BitmapFont();
        titleFont.getData().setScale(titleFontScale());
        font = new BitmapFont();
        font.getData().setScale(bodyFontScale());
        layout = new GlyphLayout();
        proj = new Matrix4();
        rebuildProj();

        prefs = new PreferencesRepository();
        if (username.isEmpty()) username = prefs.getUsername();
        selectedSkin = prefs.getSkinId();

        for (int i = 0; i < SKIN_COUNT; i++) {
            skinPreviews[i] = tryLoadTexture("characters/skin_" + i + ".png");
        }

        Gdx.input.setInputProcessor(inputAdapter);
    }

    @Override
    public void render(float delta) {
        cursorTimer += delta;
        if (cursorTimer >= 0.5f) {
            cursorTimer = 0f;
            showCursor = !showCursor;
        }

        int sw = Gdx.graphics.getWidth();
        int sh = Gdx.graphics.getHeight();

        Gdx.gl.glClearColor(0.08f, 0.08f, 0.10f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        int fieldW = fieldWidth();
        int fieldH = fieldHeight();
        int btnW = connectButtonWidth();
        int btnH = connectButtonHeight();
        int auxW = secondaryButtonWidth();
        int auxH = secondaryButtonHeight();
        int urlX = (sw - fieldW) / 2;
        int urlY = urlFieldY(sh);
        int fieldX = (sw - fieldW) / 2;
        int fieldY = usernameFieldY(sh);
        int btnX = (sw - btnW) / 2;
        int btnY = connectButtonY(sh);
        int howX = (sw - auxW) / 2;
        int howY = howBtnY(sh);
        int setX = (sw - auxW) / 2;
        int setY = setBtnY(sh);
        int swatchRowY = skinRowY(sh);
        int swatchStartX = skinStartX(sw);
        int swatchSize = swatchSize();
        int swatchGap = swatchGap();

        shapes.setProjectionMatrix(proj);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        shapes.setColor(0.18f, 0.18f, 0.22f, 1f);
        shapes.rect(urlX, urlY, fieldW, fieldH);
        drawFieldBorder(urlX, urlY, fieldW, fieldH, focusedField == 0);

        shapes.setColor(0.18f, 0.18f, 0.22f, 1f);
        shapes.rect(fieldX, fieldY, fieldW, fieldH);
        drawFieldBorder(fieldX, fieldY, fieldW, fieldH, focusedField == 1);

        shapes.setColor(0.15f, 0.50f, 0.80f, 1f);
        shapes.rect(btnX, btnY, btnW, btnH);

        for (int i = 0; i < SKIN_COUNT; i++) {
            int sx = swatchStartX + i * (swatchSize + swatchGap);
            boolean sel = selectedSkin == i;
            float[] rgb = SKIN_RGB[i];

            if (sel) {
                shapes.setColor(rgb[0] * 0.22f, rgb[1] * 0.22f, rgb[2] * 0.22f, 1f);
            } else {
                shapes.setColor(0.13f, 0.13f, 0.17f, 1f);
            }
            shapes.rect(sx, swatchRowY, swatchSize, swatchSize);

            float bw = sel ? (isAndroid() ? 4f : 2.5f) : (isAndroid() ? 2.5f : 1.5f);
            if (sel) {
                shapes.setColor(rgb[0], rgb[1], rgb[2], 1f);
            } else {
                shapes.setColor(0.32f, 0.32f, 0.40f, 1f);
            }
            shapes.rectLine(sx, swatchRowY, sx + swatchSize, swatchRowY, bw);
            shapes.rectLine(sx, swatchRowY + swatchSize, sx + swatchSize, swatchRowY + swatchSize, bw);
            shapes.rectLine(sx, swatchRowY, sx, swatchRowY + swatchSize, bw);
            shapes.rectLine(sx + swatchSize, swatchRowY, sx + swatchSize, swatchRowY + swatchSize, bw);
        }

        shapes.setColor(0.22f, 0.38f, 0.22f, 1f);
        shapes.rect(howX, howY, auxW, auxH);

        shapes.setColor(0.28f, 0.28f, 0.38f, 1f);
        shapes.rect(setX, setY, auxW, auxH);

        shapes.end();

        batch.setProjectionMatrix(proj);
        batch.begin();

        titleFont.setColor(new Color(1f, 0.82f, 0.15f, 1f));
        String title = "SHOOT 'EM N LOOT 'EM";
        layout.setText(titleFont, title);
        titleFont.draw(batch, title, (sw - layout.width) / 2f, sh * (isAndroid() ? 0.84f : 0.76f));

        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch, "Server URL", urlX, urlY + fieldH + labelOffsetY());
        font.setColor(Color.WHITE);
        String urlDisplay = serverUrl + (focusedField == 0 && showCursor ? "|" : "");
        font.draw(batch, urlDisplay, urlX + fieldPaddingX(), urlY + fieldH - fieldTextInsetY());

        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch, "Username", fieldX, fieldY + fieldH + labelOffsetY());
        font.setColor(Color.WHITE);
        String nameDisplay = username + (focusedField == 1 && showCursor ? "|" : "");
        font.draw(batch, nameDisplay, fieldX + fieldPaddingX(), fieldY + fieldH - fieldTextInsetY());

        font.setColor(Color.WHITE);
        String btnLabel = "CONNECT";
        layout.setText(font, btnLabel);
        font.draw(batch, btnLabel, btnX + (btnW - layout.width) / 2f, btnY + btnH - buttonTextInsetY());

        font.getData().setScale(sectionFontScale());
        font.setColor(0.60f, 0.60f, 0.65f, 1f);
        String skinSectionLabel = "SELECT COLOR";
        layout.setText(font, skinSectionLabel);
        font.draw(batch, skinSectionLabel, (sw - layout.width) / 2f, swatchRowY + swatchSize + sectionLabelOffsetY());
        font.getData().setScale(bodyFontScale());

        for (int i = 0; i < SKIN_COUNT; i++) {
            int sx = swatchStartX + i * (swatchSize + swatchGap);
            boolean sel = selectedSkin == i;
            float[] rgb = SKIN_RGB[i];

            if (skinPreviews[i] != null) {
                int previewPad = Math.max(4, Math.round(swatchSize * 0.08f));
                batch.setColor(sel ? 1f : 0.62f, sel ? 1f : 0.62f, sel ? 1f : 0.62f, 1f);
                batch.draw(skinPreviews[i],
                        sx + previewPad, swatchRowY + previewPad,
                        swatchSize - previewPad * 2, swatchSize - previewPad * 2,
                        0, 0, FRAME_PX, FRAME_PX,
                        false, false);
                batch.setColor(Color.WHITE);
            }

            font.getData().setScale(swatchNameFontScale());
            if (sel) {
                font.setColor(Math.min(1f, rgb[0] * 1.4f), Math.min(1f, rgb[1] * 1.4f), Math.min(1f, rgb[2] * 1.4f), 1f);
            } else {
                font.setColor(0.42f, 0.42f, 0.48f, 1f);
            }
            String skinName = SKIN_NAMES[i];
            layout.setText(font, skinName);
            font.draw(batch, skinName, sx + (swatchSize - layout.width) / 2f, swatchRowY - swatchLabelGap());
            font.getData().setScale(bodyFontScale());
        }

        font.setColor(new Color(0.55f, 0.90f, 0.55f, 1f));
        String howLabel = "HOW TO PLAY";
        layout.setText(font, howLabel);
        font.draw(batch, howLabel, howX + (auxW - layout.width) / 2f, howY + auxH - buttonTextInsetY());

        font.setColor(new Color(0.70f, 0.70f, 0.90f, 1f));
        String setLabel = "SETTINGS";
        layout.setText(font, setLabel);
        font.draw(batch, setLabel, setX + (auxW - layout.width) / 2f, setY + auxH - buttonTextInsetY());

        font.setColor(0.45f, 0.45f, 0.50f, 1f);
        font.getData().setScale(hintFontScale());
        String hint = isAndroid()
                ? "Tap a field to type  -  Tap CONNECT to join"
                : "TAB to switch fields  -  ENTER to connect";
        layout.setText(font, hint);
        font.draw(batch, hint, (sw - layout.width) / 2f, btnY - hintOffsetY());
        font.getData().setScale(bodyFontScale());

        batch.end();
    }

    @Override
    public void resize(int width, int height) {
        rebuildProj();
    }

    @Override public void pause() {}
    @Override public void resume() {}

    @Override
    public void hide() {
        hideSoftKeyboardIfNeeded();
        Gdx.input.setInputProcessor(null);
    }

    @Override
    public void dispose() {
        if (shapes != null) shapes.dispose();
        if (batch != null) batch.dispose();
        if (titleFont != null) titleFont.dispose();
        if (font != null) font.dispose();
        for (Texture t : skinPreviews) if (t != null) t.dispose();
    }

    private int skinRowY(int sh) {
        return connectButtonY(sh) - skinRowOffset();
    }

    private int skinStartX(int sw) {
        int totalW = SKIN_COUNT * swatchSize() + (SKIN_COUNT - 1) * swatchGap();
        return (sw - totalW) / 2;
    }

    private int howBtnY(int sh) {
        return skinRowY(sh) - lowerButtonGap() - secondaryButtonHeight();
    }

    private int setBtnY(int sh) {
        return howBtnY(sh) - settingsButtonGap() - secondaryButtonHeight();
    }

    private void drawFieldBorder(int x, int y, int w, int h, boolean focused) {
        shapes.setColor(focused ? 0.45f : 0.28f,
                focused ? 0.65f : 0.28f,
                focused ? 0.95f : 0.35f, 1f);
        float bw = borderWidth();
        shapes.rectLine(x, y, x + w, y, bw);
        shapes.rectLine(x, y + h, x + w, y + h, bw);
        shapes.rectLine(x, y, x, y + h, bw);
        shapes.rectLine(x + w, y, x + w, y + h, bw);
    }

    private static boolean hit(int px, int py, int rx, int ry, int rw, int rh) {
        return px >= rx && px <= rx + rw && py >= ry && py <= ry + rh;
    }

    private void rebuildProj() {
        if (proj == null) proj = new Matrix4();
        proj.setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    private void tryConnect() {
        String url = serverUrl.trim();
        String name = username.trim();
        if (!url.isEmpty() && !name.isEmpty()) {
            hideSoftKeyboardIfNeeded();
            prefs.saveUsername(name);
            prefs.setSkinId(selectedSkin);
            navigator.showGame(url, name);
        }
    }

    private boolean isAndroid() {
        return Gdx.app.getType() == Application.ApplicationType.Android;
    }

    private float menuScale() {
        return isAndroid() ? 1.65f : 1f;
    }

    private int fieldWidth() {
        return Math.round(FIELD_W * menuScale());
    }

    private int fieldHeight() {
        return Math.round(FIELD_H * menuScale());
    }

    private int connectButtonWidth() {
        return Math.round(BTN_W * menuScale());
    }

    private int connectButtonHeight() {
        return Math.round(BTN_H * menuScale());
    }

    private int secondaryButtonWidth() {
        return Math.round(HOW_BTN_W * menuScale());
    }

    private int secondaryButtonHeight() {
        return Math.round(HOW_BTN_H * menuScale());
    }

    private int swatchSize() {
        return Math.round(SWATCH_SIZE * menuScale());
    }

    private int swatchGap() {
        return Math.round(SWATCH_GAP * menuScale());
    }

    private int urlFieldY(int sh) {
        return sh / 2 + Math.round(60f * menuScale());
    }

    private int usernameFieldY(int sh) {
        return sh / 2 - Math.round(10f * menuScale());
    }

    private int connectButtonY(int sh) {
        return sh / 2 - Math.round(90f * menuScale());
    }

    private int skinRowOffset() {
        return Math.round(175f * menuScale());
    }

    private int lowerButtonGap() {
        return Math.round(28f * menuScale());
    }

    private int settingsButtonGap() {
        return Math.round(16f * menuScale());
    }

    private float titleFontScale() {
        return isAndroid() ? 4.8f : 3.2f;
    }

    private float bodyFontScale() {
        return isAndroid() ? 2.15f : 1.5f;
    }

    private float sectionFontScale() {
        return isAndroid() ? 1.55f : 1.1f;
    }

    private float swatchNameFontScale() {
        return isAndroid() ? 1.30f : 0.95f;
    }

    private float hintFontScale() {
        return isAndroid() ? 1.35f : 1.1f;
    }

    private float borderWidth() {
        return isAndroid() ? 3f : 2f;
    }

    private float labelOffsetY() {
        return isAndroid() ? 34f : 24f;
    }

    private float fieldPaddingX() {
        return isAndroid() ? 16f : 10f;
    }

    private float fieldTextInsetY() {
        return isAndroid() ? 10f : 6f;
    }

    private float buttonTextInsetY() {
        return isAndroid() ? 16f : 10f;
    }

    private float sectionLabelOffsetY() {
        return isAndroid() ? 34f : 22f;
    }

    private float swatchLabelGap() {
        return isAndroid() ? 10f : 5f;
    }

    private float hintOffsetY() {
        return isAndroid() ? 30f : 18f;
    }

    private void showSoftKeyboardIfNeeded() {
        if (isAndroid()) Gdx.input.setOnscreenKeyboardVisible(true);
    }

    private void hideSoftKeyboardIfNeeded() {
        if (isAndroid()) Gdx.input.setOnscreenKeyboardVisible(false);
    }

    private static Texture tryLoadTexture(String path) {
        try {
            com.badlogic.gdx.files.FileHandle fh = Gdx.files.internal(path);
            if (fh.exists()) {
                Texture t = new Texture(fh);
                t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
                return t;
            }
        } catch (Exception ignored) {}
        return null;
    }
}
