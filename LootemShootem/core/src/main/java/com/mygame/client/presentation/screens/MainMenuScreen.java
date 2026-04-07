package com.mygame.client.presentation.screens;

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

    // ── Layout constants ──────────────────────────────────────────────────────
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

    // ── Skin selector constants ───────────────────────────────────────────────
    private static final int   SKIN_COUNT  = 4;
    private static final int   SWATCH_SIZE = 52;  // each skin preview box (px)
    private static final int   SWATCH_GAP  = 10;  // gap between boxes
    /** Frame size (px) inside the 256×64 skin spritesheet. */
    private static final int   FRAME_PX    = 64;

    private static final String[] SKIN_NAMES = { "WARRIOR", "RANGER", "SCOUT", "MAGE" };
    /** Accent colors that match the generated skin palettes. */
    private static final float[][] SKIN_RGB = {
        { 0.74f, 0.18f, 0.18f },  // warrior – red
        { 0.16f, 0.30f, 0.76f },  // ranger  – blue
        { 0.14f, 0.61f, 0.22f },  // scout   – green
        { 0.49f, 0.14f, 0.76f },  // mage    – purple
    };

    // ── State ─────────────────────────────────────────────────────────────────
    private final Navigator navigator;
    private String serverUrl;
    private String username;
    private int    selectedSkin = 0;

    private PreferencesPort prefs;
    private int focusedField = 1; // 0 = server URL, 1 = username

    // ── Rendering resources (created in show()) ───────────────────────────────
    private ShapeRenderer shapes;
    private SpriteBatch   batch;
    private BitmapFont    titleFont;
    private BitmapFont    font;
    private GlyphLayout   layout;
    private Matrix4       proj;

    /** Idle-frame (frame 0) preview textures for each skin. Null = use colored fallback. */
    private final Texture[] skinPreviews = new Texture[SKIN_COUNT];

    // ── Cursor blink ─────────────────────────────────────────────────────────
    private float   cursorTimer = 0f;
    private boolean showCursor  = true;

    // ── Input handler ─────────────────────────────────────────────────────────
    private final InputAdapter inputAdapter = new InputAdapter() {
        @Override
        public boolean keyDown(int keycode) {
            if (keycode == Keys.TAB) {
                focusedField = 1 - focusedField;
                return true;
            }
            if (keycode == Keys.BACKSPACE) {
                if (focusedField == 0 && serverUrl.length() > 0)
                    serverUrl = serverUrl.substring(0, serverUrl.length() - 1);
                else if (focusedField == 1 && username.length() > 0)
                    username = username.substring(0, username.length() - 1);
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
            int sw     = Gdx.graphics.getWidth();
            int sh     = Gdx.graphics.getHeight();
            int worldY = sh - screenY;

            // ── Text fields ───────────────────────────────────────────────────
            int urlX  = (sw - FIELD_W) / 2;
            int urlY  = sh / 2 + 60;
            if (hit(screenX, worldY, urlX, urlY, FIELD_W, FIELD_H)) {
                focusedField = 0;
                return true;
            }
            int fieldX = (sw - FIELD_W) / 2;
            int fieldY = sh / 2 - 10;
            if (hit(screenX, worldY, fieldX, fieldY, FIELD_W, FIELD_H)) {
                focusedField = 1;
                return true;
            }

            // ── CONNECT ───────────────────────────────────────────────────────
            int btnX = (sw - BTN_W) / 2;
            int btnY = sh / 2 - 90;
            if (hit(screenX, worldY, btnX, btnY, BTN_W, BTN_H)) {
                tryConnect();
                return true;
            }

            // ── Skin swatches ─────────────────────────────────────────────────
            int swatchRowY   = skinRowY(sh);
            int swatchStartX = skinStartX(sw);
            for (int i = 0; i < SKIN_COUNT; i++) {
                int sx = swatchStartX + i * (SWATCH_SIZE + SWATCH_GAP);
                if (hit(screenX, worldY, sx, swatchRowY, SWATCH_SIZE, SWATCH_SIZE)) {
                    selectedSkin = i;
                    prefs.setSkinId(i);
                    return true;
                }
            }

            // ── HOW TO PLAY ───────────────────────────────────────────────────
            int howX = (sw - HOW_BTN_W) / 2;
            int howY = howBtnY(sh);
            if (hit(screenX, worldY, howX, howY, HOW_BTN_W, HOW_BTN_H)) {
                navigator.showTutorial(serverUrl, username);
                return true;
            }

            // ── SETTINGS ──────────────────────────────────────────────────────
            int setX = (sw - SET_BTN_W) / 2;
            int setY = setBtnY(sh);
            if (hit(screenX, worldY, setX, setY, SET_BTN_W, SET_BTN_H)) {
                navigator.showSettings(serverUrl, username);
                return true;
            }
            return false;
        }
    };

    // ── Constructor ───────────────────────────────────────────────────────────

    public MainMenuScreen(Navigator navigator, String serverUrl, String defaultUsername) {
        this.navigator = navigator;
        this.serverUrl = serverUrl != null ? serverUrl : "";
        this.username  = defaultUsername != null ? defaultUsername : "";
    }

    // ── Screen lifecycle ──────────────────────────────────────────────────────

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
        selectedSkin = prefs.getSkinId();

        // Load skin preview textures (frame 0 of each 256×64 strip)
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
            showCursor  = !showCursor;
        }

        int sw = Gdx.graphics.getWidth();
        int sh = Gdx.graphics.getHeight();

        Gdx.gl.glClearColor(0.08f, 0.08f, 0.10f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Cached layout positions
        int urlX   = (sw - FIELD_W)   / 2;
        int urlY   = sh / 2 + 60;
        int fieldX = (sw - FIELD_W)   / 2;
        int fieldY = sh / 2 - 10;
        int btnX   = (sw - BTN_W)     / 2;
        int btnY   = sh / 2 - 90;
        int howX   = (sw - HOW_BTN_W) / 2;
        int howY   = howBtnY(sh);
        int setX   = (sw - SET_BTN_W) / 2;
        int setY   = setBtnY(sh);

        // Skin selector
        int swatchRowY   = skinRowY(sh);
        int swatchStartX = skinStartX(sw);

        // ── Shape pass ────────────────────────────────────────────────────────
        shapes.setProjectionMatrix(proj);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        // Server URL field
        shapes.setColor(0.18f, 0.18f, 0.22f, 1f);
        shapes.rect(urlX, urlY, FIELD_W, FIELD_H);
        drawFieldBorder(urlX, urlY, focusedField == 0);

        // Username field
        shapes.setColor(0.18f, 0.18f, 0.22f, 1f);
        shapes.rect(fieldX, fieldY, FIELD_W, FIELD_H);
        drawFieldBorder(fieldX, fieldY, focusedField == 1);

        // CONNECT button
        shapes.setColor(0.15f, 0.50f, 0.80f, 1f);
        shapes.rect(btnX, btnY, BTN_W, BTN_H);

        // ── Skin swatches ─────────────────────────────────────────────────────
        for (int i = 0; i < SKIN_COUNT; i++) {
            int sx = swatchStartX + i * (SWATCH_SIZE + SWATCH_GAP);
            boolean sel = (selectedSkin == i);
            float[] rgb = SKIN_RGB[i];

            // Box background — selected: slightly tinted, others: neutral dark
            if (sel) {
                shapes.setColor(rgb[0] * 0.22f, rgb[1] * 0.22f, rgb[2] * 0.22f, 1f);
            } else {
                shapes.setColor(0.13f, 0.13f, 0.17f, 1f);
            }
            shapes.rect(sx, swatchRowY, SWATCH_SIZE, SWATCH_SIZE);

            // Border — selected: bright skin color, others: dim
            float bw = sel ? 2.5f : 1.5f;
            if (sel) {
                shapes.setColor(rgb[0], rgb[1], rgb[2], 1f);
            } else {
                shapes.setColor(0.32f, 0.32f, 0.40f, 1f);
            }
            shapes.rectLine(sx,             swatchRowY,              sx + SWATCH_SIZE, swatchRowY,              bw);
            shapes.rectLine(sx,             swatchRowY + SWATCH_SIZE, sx + SWATCH_SIZE, swatchRowY + SWATCH_SIZE, bw);
            shapes.rectLine(sx,             swatchRowY,              sx,               swatchRowY + SWATCH_SIZE, bw);
            shapes.rectLine(sx + SWATCH_SIZE, swatchRowY,            sx + SWATCH_SIZE, swatchRowY + SWATCH_SIZE, bw);
        }

        // HOW TO PLAY button
        shapes.setColor(0.22f, 0.38f, 0.22f, 1f);
        shapes.rect(howX, howY, HOW_BTN_W, HOW_BTN_H);

        // SETTINGS button
        shapes.setColor(0.28f, 0.28f, 0.38f, 1f);
        shapes.rect(setX, setY, SET_BTN_W, SET_BTN_H);

        shapes.end();

        // ── Sprite + text pass ────────────────────────────────────────────────
        batch.setProjectionMatrix(proj);
        batch.begin();

        // Title
        titleFont.setColor(new Color(1f, 0.82f, 0.15f, 1f));
        String title = "SHOOT 'EM N LOOT 'EM";
        layout.setText(titleFont, title);
        titleFont.draw(batch, title, (sw - layout.width) / 2f, sh * 0.76f);

        // Server URL field
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch, "Server URL", urlX, urlY + FIELD_H + 24f);
        font.setColor(Color.WHITE);
        String urlDisplay = serverUrl + (focusedField == 0 && showCursor ? "|" : "");
        font.draw(batch, urlDisplay, urlX + 10f, urlY + FIELD_H - 6f);

        // Username field
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch, "Username", fieldX, fieldY + FIELD_H + 24f);
        font.setColor(Color.WHITE);
        String nameDisplay = username + (focusedField == 1 && showCursor ? "|" : "");
        font.draw(batch, nameDisplay, fieldX + 10f, fieldY + FIELD_H - 6f);

        // CONNECT button label
        font.setColor(Color.WHITE);
        String btnLabel = "CONNECT";
        layout.setText(font, btnLabel);
        font.draw(batch, btnLabel, btnX + (BTN_W - layout.width) / 2f, btnY + BTN_H - 10f);

        // ── Skin selector: label, previews, names ─────────────────────────────
        font.getData().setScale(1.1f);
        font.setColor(0.60f, 0.60f, 0.65f, 1f);
        String skinSectionLabel = "SELECT CLASS";
        layout.setText(font, skinSectionLabel);
        font.draw(batch, skinSectionLabel,
                (sw - layout.width) / 2f,
                swatchRowY + SWATCH_SIZE + 22f);
        font.getData().setScale(1.5f);

        for (int i = 0; i < SKIN_COUNT; i++) {
            int sx = swatchStartX + i * (SWATCH_SIZE + SWATCH_GAP);
            boolean sel = (selectedSkin == i);
            float[] rgb = SKIN_RGB[i];

            // Skin sprite preview (frame 0 of the 256×64 strip = srcX:0, srcW:64)
            if (skinPreviews[i] != null) {
                int pad = 4;
                batch.setColor(sel ? 1f : 0.62f, sel ? 1f : 0.62f, sel ? 1f : 0.62f, 1f);
                batch.draw(skinPreviews[i],
                        sx + pad, swatchRowY + pad,
                        SWATCH_SIZE - pad * 2, SWATCH_SIZE - pad * 2,
                        0, 0, FRAME_PX, FRAME_PX,
                        false, false);
                batch.setColor(Color.WHITE);
            } else {
                // Colored circle fallback when texture not found
                // (ShapeRenderer can't be used here, so we just skip — box color above suffices)
            }

            // Skin name below the swatch
            font.getData().setScale(0.95f);
            if (sel) {
                font.setColor(rgb[0] * 1.4f > 1f ? 1f : rgb[0] * 1.4f,
                              rgb[1] * 1.4f > 1f ? 1f : rgb[1] * 1.4f,
                              rgb[2] * 1.4f > 1f ? 1f : rgb[2] * 1.4f,
                              1f);
            } else {
                font.setColor(0.42f, 0.42f, 0.48f, 1f);
            }
            String skinName = SKIN_NAMES[i];
            layout.setText(font, skinName);
            font.draw(batch, skinName,
                    sx + (SWATCH_SIZE - layout.width) / 2f,
                    swatchRowY - 5f);
            font.getData().setScale(1.5f);
        }

        // HOW TO PLAY label
        font.setColor(new Color(0.55f, 0.90f, 0.55f, 1f));
        String howLabel = "HOW TO PLAY";
        layout.setText(font, howLabel);
        font.draw(batch, howLabel, howX + (HOW_BTN_W - layout.width) / 2f, howY + HOW_BTN_H - 10f);

        // SETTINGS label
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
        for (Texture t : skinPreviews) if (t != null) t.dispose();
    }

    // ── Position helpers (centralised so render and touchDown stay in sync) ───

    /** Bottom Y of the skin swatches row. */
    private static int skinRowY(int sh) {
        // Below the hint text (which is at sh/2 - 108), with some breathing room
        return sh / 2 - 175;
    }

    /** Left X of the first swatch so the row is centred. */
    private static int skinStartX(int sw) {
        int totalW = SKIN_COUNT * SWATCH_SIZE + (SKIN_COUNT - 1) * SWATCH_GAP;
        return (sw - totalW) / 2;
    }

    /** Bottom Y of the HOW TO PLAY button (shifted down to make room for skins). */
    private static int howBtnY(int sh) {
        return sh / 2 - 252;
    }

    /** Bottom Y of the SETTINGS button. */
    private static int setBtnY(int sh) {
        return sh / 2 - 308;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void drawFieldBorder(int x, int y, boolean focused) {
        shapes.setColor(focused ? 0.45f : 0.28f,
                        focused ? 0.65f : 0.28f,
                        focused ? 0.95f : 0.35f, 1f);
        shapes.rectLine(x,          y,          x + FIELD_W,  y,          2f);
        shapes.rectLine(x,          y + FIELD_H, x + FIELD_W, y + FIELD_H, 2f);
        shapes.rectLine(x,          y,          x,            y + FIELD_H, 2f);
        shapes.rectLine(x + FIELD_W, y,         x + FIELD_W,  y + FIELD_H, 2f);
    }

    /** AABB hit test in world coordinates (y=0 at bottom). */
    private static boolean hit(int px, int py, int rx, int ry, int rw, int rh) {
        return px >= rx && px <= rx + rw && py >= ry && py <= ry + rh;
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
            prefs.setSkinId(selectedSkin);
            navigator.showGame(url, name);
        }
    }

    /** Loads a texture from internal assets. Returns null if missing (no crash). */
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
