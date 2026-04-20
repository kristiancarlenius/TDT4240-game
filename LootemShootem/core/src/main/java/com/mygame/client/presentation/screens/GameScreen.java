package com.mygame.client.presentation.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.mygame.client.application.service.GameSessionService;
import com.mygame.client.application.usecase.ApplySnapshotUseCase;
import com.mygame.client.application.usecase.SendInputUseCase;
import com.mygame.client.data.repository.PreferencesRepository;
import com.mygame.client.domain.model.WorldState;
import com.mygame.client.domain.ports.PreferencesPort;
import com.mygame.client.presentation.controller.GameController;
import com.mygame.client.presentation.navigation.Navigator;
import com.mygame.client.presentation.view.input.InputHandler;
import com.mygame.client.presentation.view.renderer.HudRenderer;
import com.mygame.client.presentation.view.renderer.WorldRenderer;

public final class GameScreen implements Screen {

    private static final int PAUSE_BTN_SIZE = 56;
    private static final int PAUSE_BTN_MARGIN = 16;
    private static final int OVERLAY_BTN_W = 300;
    private static final int OVERLAY_BTN_H = 54;
    private static final int TOGGLE_BTN_W = 300;
    private static final int TOGGLE_BTN_H = 46;
    private static final int SLIDER_W = 320;
    private static final int SLIDER_H = 10;
    private static final int SLIDER_HANDLE_R = 12;

    private enum DragControl {
        NONE,
        MUSIC_VOLUME,
        JOYSTICK_SIZE,
        JOYSTICK_OPACITY
    }

    private final Navigator navigator;
    private final String serverUrl;
    private final String username;

    private GameSessionService session;
    private GameController controller;
    private WorldRenderer worldRenderer;
    private HudRenderer hudRenderer;
    private InputHandler inputHandler;

    private OrthographicCamera camera;
    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private BitmapFont font;
    private BitmapFont bigFont;
    private GlyphLayout layout;
    private Matrix4 screenProj;

    private Music backgroundMusic;
    private PreferencesPort prefs;
    private boolean paused = false;
    private boolean showControlsPanel = false;
    private boolean navigatingToMenu = false;
    private boolean disposed = false;
    private DragControl dragControl = DragControl.NONE;

    public GameScreen(Navigator navigator, String serverUrl, String username) {
        this.navigator = navigator;
        this.serverUrl = serverUrl;
        this.username = username;
    }

    @Override
    public void show() {
        camera = new OrthographicCamera();
        camera.setToOrtho(false, 32f, 18f);
        camera.update();

        shapes = new ShapeRenderer();
        batch = new SpriteBatch();
        font = new BitmapFont();
        font.getData().setScale(1.4f);
        bigFont = new BitmapFont();
        bigFont.getData().setScale(3.5f);
        layout = new GlyphLayout();
        screenProj = new Matrix4();
        rebuildScreenProj();

        WorldState worldState = new WorldState();
        ApplySnapshotUseCase applySnapshot = new ApplySnapshotUseCase(worldState);
        session = new GameSessionService(
                worldState,
                applySnapshot,
                () -> Gdx.app.postRunnable(this::handleSessionDisconnected));
        SendInputUseCase sendInput = new SendInputUseCase(session);

        prefs = new PreferencesRepository();
        inputHandler = new InputHandler(camera);
        controller = new GameController(worldState, inputHandler, sendInput);
        worldRenderer = new WorldRenderer(worldState, camera, shapes, batch);
        hudRenderer = new HudRenderer(worldState, inputHandler, shapes, batch, font, bigFont, prefs);

        try {
            com.badlogic.gdx.files.FileHandle musicFile = Gdx.files.internal("Boney_M_Rasputin.ogg");
            if (!musicFile.exists()) musicFile = Gdx.files.internal("sound/background_music.ogg");
            if (musicFile.exists()) {
                backgroundMusic = Gdx.audio.newMusic(musicFile);
                backgroundMusic.setLooping(true);
                applyMusicPreferences();
            }
        } catch (Exception e) {
            System.err.println("[AUDIO] Error loading music: " + e.getMessage());
        }

        session.connect(serverUrl, username, prefs.getSkinId());
    }

    @Override
    public void render(float delta) {
        handlePauseInput();

        if (!paused) controller.update(delta);
        worldRenderer.updateCamera();

        Gdx.gl.glClearColor(0.08f, 0.08f, 0.10f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        worldRenderer.render();
        hudRenderer.render();
        drawPauseButton();
        if (paused) drawPauseOverlay();
    }

    @Override
    public void resize(int width, int height) {
        rebuildScreenProj();
        if (inputHandler != null) inputHandler.refreshMobileLayout();
        if (hudRenderer != null) hudRenderer.resize();
    }

    @Override public void pause() {}
    @Override public void resume() {}

    @Override
    public void hide() {
        if (inputHandler != null) inputHandler.clearInputProcessor();
        if (backgroundMusic != null) backgroundMusic.stop();
    }

    @Override
    public void dispose() {
        disposed = true;
        if (session != null) session.disconnect();
        if (worldRenderer != null) worldRenderer.dispose();
        if (shapes != null) shapes.dispose();
        if (batch != null) batch.dispose();
        if (font != null) font.dispose();
        if (bigFont != null) bigFont.dispose();
        if (backgroundMusic != null) backgroundMusic.dispose();
    }

    private void handlePauseInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || Gdx.input.isKeyJustPressed(Input.Keys.BACK)) {
            togglePause();
        }
        if (inputHandler != null && inputHandler.consumePauseToggle()) {
            togglePause();
        }

        if (!paused) {
            dragControl = DragControl.NONE;
            return;
        }

        if (!Gdx.input.isTouched()) {
            dragControl = DragControl.NONE;
            return;
        }

        float x = Gdx.input.getX();
        float y = Gdx.graphics.getHeight() - Gdx.input.getY();

        if (Gdx.input.justTouched()) {
            if (isInsideButton(x, y, overlayButtonX(), overlayResumeY(), OVERLAY_BTN_W, OVERLAY_BTN_H)) {
                paused = false;
                return;
            }
            if (isInsideButton(x, y, overlayButtonX(), overlayLeaveY(), OVERLAY_BTN_W, OVERLAY_BTN_H)) {
                leaveMatch();
                return;
            }
            if (isInsideButton(x, y, overlayButtonX(), overlayMuteY(), TOGGLE_BTN_W, TOGGLE_BTN_H)) {
                prefs.setMusicEnabled(!prefs.isMusicEnabled());
                applyMusicPreferences();
                return;
            }
            if (isInsideButton(x, y, overlayButtonX(), overlayControlsY(), TOGGLE_BTN_W, TOGGLE_BTN_H)) {
                showControlsPanel = !showControlsPanel;
                return;
            }
            if (inputHandler.isAndroid()
                    && isInsideButton(x, y, overlayButtonX(), overlaySwapY(), TOGGLE_BTN_W, TOGGLE_BTN_H)) {
                prefs.setControlsSwapped(!prefs.isControlsSwapped());
                inputHandler.refreshMobileLayout();
                inputHandler.resetTouchState();
                return;
            }
            dragControl = touchedSlider(x, y);
            if (dragControl != DragControl.NONE) {
                applySliderDrag(x);
            }
            return;
        }

        if (dragControl != DragControl.NONE) {
            applySliderDrag(x);
        }
    }

    private void togglePause() {
        paused = !paused;
        dragControl = DragControl.NONE;
        if (!paused) showControlsPanel = false;
        if (inputHandler != null) inputHandler.resetTouchState();
    }

    private DragControl touchedSlider(float x, float y) {
        if (isInsideButton(x, y, sliderX(), overlayVolumeY(), SLIDER_W, 28f)) return DragControl.MUSIC_VOLUME;
        if (inputHandler.isAndroid()) {
            if (isInsideButton(x, y, sliderX(), overlayStickSizeY(), SLIDER_W, 28f)) return DragControl.JOYSTICK_SIZE;
            if (isInsideButton(x, y, sliderX(), overlayStickOpacityY(), SLIDER_W, 28f)) return DragControl.JOYSTICK_OPACITY;
        }
        return DragControl.NONE;
    }

    private void applySliderDrag(float x) {
        float t = Math.max(0f, Math.min(1f, (x - sliderX()) / SLIDER_W));
        switch (dragControl) {
            case MUSIC_VOLUME:
                prefs.setMusicVolume(t);
                applyMusicPreferences();
                break;
            case JOYSTICK_SIZE:
                prefs.setTouchJoystickScale(0.75f + t * 0.70f);
                inputHandler.refreshMobileLayout();
                break;
            case JOYSTICK_OPACITY:
                prefs.setTouchJoystickOpacity(0.25f + t * 0.75f);
                inputHandler.refreshMobileLayout();
                break;
            default:
                break;
        }
    }

    private void drawPauseButton() {
        if (inputHandler == null || !inputHandler.isAndroid()) return;

        float x = inputHandler.getPauseBtnX();
        float y = inputHandler.getPauseBtnY();

        shapes.setProjectionMatrix(screenProj);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.45f);
        shapes.rect(x, y, PAUSE_BTN_SIZE, PAUSE_BTN_SIZE);
        shapes.setColor(0.92f, 0.92f, 0.92f, 0.9f);
        shapes.rect(x + 16f, y + 12f, 8f, 32f);
        shapes.rect(x + 32f, y + 12f, 8f, 32f);
        shapes.end();
    }

    private void drawPauseOverlay() {
        int sw = Gdx.graphics.getWidth();
        int sh = Gdx.graphics.getHeight();

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapes.setProjectionMatrix(screenProj);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.72f);
        shapes.rect(0, 0, sw, sh);
        drawOverlayButtonShape(overlayResumeY(), 0.22f, 0.55f, 0.28f, OVERLAY_BTN_W, OVERLAY_BTN_H);
        drawOverlayButtonShape(overlayLeaveY(), 0.60f, 0.20f, 0.20f, OVERLAY_BTN_W, OVERLAY_BTN_H);
        drawOverlayButtonShape(overlayMuteY(), 0.28f, 0.33f, 0.58f, TOGGLE_BTN_W, TOGGLE_BTN_H);
        drawOverlayButtonShape(overlayControlsY(), 0.36f, 0.36f, 0.46f, TOGGLE_BTN_W, TOGGLE_BTN_H);
        if (inputHandler.isAndroid()) {
            drawOverlayButtonShape(overlaySwapY(), 0.33f, 0.40f, 0.66f, TOGGLE_BTN_W, TOGGLE_BTN_H);
        }
        drawSliderShape(overlayVolumeY(), sliderFraction(prefs.getMusicVolume(), 0f, 1f), 0.92f, 0.73f, 0.20f);
        if (inputHandler.isAndroid()) {
            drawSliderShape(overlayStickSizeY(), sliderFraction(prefs.getTouchJoystickScale(), 0.75f, 1.45f), 0.25f, 0.76f, 0.90f);
            drawSliderShape(overlayStickOpacityY(), sliderFraction(prefs.getTouchJoystickOpacity(), 0.25f, 1f), 0.40f, 0.88f, 0.55f);
        }
        shapes.end();

        batch.setProjectionMatrix(screenProj);
        batch.begin();
        bigFont.draw(batch, "PAUSED", centeredX("PAUSED", bigFont), sh * 0.86f);
        drawOverlayButtonText("RESUME", overlayResumeY(), OVERLAY_BTN_H);
        drawOverlayButtonText("LEAVE MATCH", overlayLeaveY(), OVERLAY_BTN_H);
        drawOverlayButtonText("MUSIC: " + (prefs.isMusicEnabled() ? "ON" : "MUTED"), overlayMuteY(), TOGGLE_BTN_H);
        drawOverlayButtonText(showControlsPanel ? "CONTROLS: HIDE" : "CONTROLS: SHOW", overlayControlsY(), TOGGLE_BTN_H);
        if (inputHandler.isAndroid()) {
            drawOverlayButtonText("CONTROLS: " + (prefs.isControlsSwapped() ? "LEFT-HANDED" : "RIGHT-HANDED"), overlaySwapY(), TOGGLE_BTN_H);
        }
        drawSliderLabel("Music Volume  " + Math.round(prefs.getMusicVolume() * 100f) + "%", overlayVolumeY() + 44f);
        if (inputHandler.isAndroid()) {
            drawSliderLabel("Joystick Size  " + Math.round(prefs.getTouchJoystickScale() * 100f) + "%", overlayStickSizeY() + 44f);
            drawSliderLabel("Joystick Opacity  " + Math.round(prefs.getTouchJoystickOpacity() * 100f) + "%", overlayStickOpacityY() + 44f);
        } else {
            font.draw(batch, "ESC/BACK resumes", centeredX("ESC/BACK resumes", font), sh * 0.15f);
        }
        if (showControlsPanel) drawControlsPanel(sw, sh);
        batch.end();
    }

    private void drawControlsPanel(int sw, int sh) {
        float panelW = 420f;
        float panelH = inputHandler.isAndroid() ? 170f : 92f;
        float panelX = (sw - panelW) * 0.5f;
        float panelY = sh * 0.08f;
        boolean leftHanded = prefs.isControlsSwapped();

        batch.end();
        shapes.setProjectionMatrix(screenProj);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.08f, 0.08f, 0.10f, 0.94f);
        shapes.rect(panelX, panelY, panelW, panelH);
        shapes.setColor(0.34f, 0.38f, 0.48f, 1f);
        shapes.rect(panelX, panelY + panelH - 34f, panelW, 34f);
        shapes.end();

        batch.begin();
        font.draw(batch, "CONTROLS", panelX + 18f, panelY + panelH - 10f);
        if (inputHandler.isAndroid()) {
            font.draw(batch, leftHanded ? "MOVE: right stick" : "MOVE: left stick", panelX + 18f, panelY + panelH - 52f);
            font.draw(batch, leftHanded ? "AIM: left stick outer ring" : "AIM: right stick outer ring", panelX + 18f, panelY + panelH - 80f);
            font.draw(batch, leftHanded ? "SHOOT: left stick inner ring" : "SHOOT: right stick inner ring", panelX + 18f, panelY + panelH - 108f);
            font.draw(batch, "SWAP: switch weapon   LOAD: reload", panelX + 18f, panelY + panelH - 136f);
        } else {
            font.draw(batch, "MOVE: WASD   AIM/FIRE: mouse", panelX + 18f, panelY + panelH - 58f);
            font.draw(batch, "SWAP: SPACE   RELOAD: R", panelX + 18f, panelY + panelH - 86f);
        }
    }

    private void drawOverlayButtonShape(float y, float r, float g, float b, float w, float h) {
        shapes.setColor(r, g, b, 0.95f);
        shapes.rect((Gdx.graphics.getWidth() - w) * 0.5f, y, w, h);
    }

    private void drawSliderShape(float y, float fraction, float r, float g, float b) {
        float x = sliderX();
        shapes.setColor(0.18f, 0.18f, 0.22f, 0.95f);
        shapes.rect(x, y + 9f, SLIDER_W, SLIDER_H);
        shapes.setColor(r, g, b, 0.98f);
        shapes.rect(x, y + 9f, SLIDER_W * fraction, SLIDER_H);
        shapes.circle(x + SLIDER_W * fraction, y + 14f, SLIDER_HANDLE_R, 20);
    }

    private void drawOverlayButtonText(String text, float y, float h) {
        font.draw(batch, text, centeredX(text, font), y + h - 13f);
    }

    private void drawSliderLabel(String text, float y) {
        font.draw(batch, text, centeredX(text, font), y);
    }

    private float centeredX(String text, BitmapFont bitmapFont) {
        layout.setText(bitmapFont, text);
        return (Gdx.graphics.getWidth() - layout.width) * 0.5f;
    }

    private boolean isInsideButton(float x, float y, float bx, float by, float bw, float bh) {
        return x >= bx && x <= bx + bw && y >= by && y <= by + bh;
    }

    private float overlayButtonX() {
        return (Gdx.graphics.getWidth() - OVERLAY_BTN_W) * 0.5f;
    }

    private float sliderX() {
        return (Gdx.graphics.getWidth() - SLIDER_W) * 0.5f;
    }

    private float overlayResumeY() {
        return Gdx.graphics.getHeight() * 0.70f;
    }

    private float overlayLeaveY() {
        return Gdx.graphics.getHeight() * 0.60f;
    }

    private float overlayMuteY() {
        return Gdx.graphics.getHeight() * 0.50f;
    }

    private float overlayControlsY() {
        return Gdx.graphics.getHeight() * 0.42f;
    }

    private float overlaySwapY() {
        return Gdx.graphics.getHeight() * 0.34f;
    }

    private float overlayVolumeY() {
        return inputHandler.isAndroid() ? Gdx.graphics.getHeight() * 0.25f : Gdx.graphics.getHeight() * 0.33f;
    }

    private float overlayStickSizeY() {
        return Gdx.graphics.getHeight() * 0.17f;
    }

    private float overlayStickOpacityY() {
        return Gdx.graphics.getHeight() * 0.09f;
    }

    private float sliderFraction(float value, float min, float max) {
        return Math.max(0f, Math.min(1f, (value - min) / (max - min)));
    }

    private void rebuildScreenProj() {
        if (screenProj == null) screenProj = new Matrix4();
        screenProj.setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    private void leaveMatch() {
        if (navigatingToMenu) return;
        navigatingToMenu = true;
        navigator.showMainMenu(serverUrl, username);
    }

    private void handleSessionDisconnected() {
        if (disposed || navigatingToMenu) return;
        leaveMatch();
    }

    private void applyMusicPreferences() {
        if (backgroundMusic == null || prefs == null) return;
        backgroundMusic.setVolume(prefs.getMusicVolume());
        if (prefs.isMusicEnabled()) {
            if (!backgroundMusic.isPlaying()) backgroundMusic.play();
        } else {
            backgroundMusic.pause();
        }
    }
}
