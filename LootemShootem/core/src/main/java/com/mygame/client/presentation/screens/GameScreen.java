package com.mygame.client.presentation.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
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

    private final Navigator navigator;
    private final String    serverUrl;
    private final String    username;

    private GameSessionService session;
    private GameController     controller;
    private WorldRenderer      worldRenderer;
    private HudRenderer        hudRenderer;
    private InputHandler       inputHandler;

    private OrthographicCamera camera;
    private ShapeRenderer      shapes;
    private SpriteBatch        batch;
    private BitmapFont         font;
    private BitmapFont         bigFont;

    private Music backgroundMusic;

    public GameScreen(Navigator navigator, String serverUrl, String username) {
        this.navigator = navigator;
        this.serverUrl = serverUrl;
        this.username  = username;
    }

    @Override
    public void show() {
        camera = new OrthographicCamera();
        camera.setToOrtho(false, 32f, 18f);
        camera.update();

        shapes  = new ShapeRenderer();
        batch   = new SpriteBatch();
        font    = new BitmapFont();
        font.getData().setScale(1.4f);
        bigFont = new BitmapFont();
        bigFont.getData().setScale(3.5f);

        WorldState worldState = new WorldState();

        ApplySnapshotUseCase applySnapshot = new ApplySnapshotUseCase(worldState);
        session = new GameSessionService(
                worldState,
                applySnapshot,
                () -> Gdx.app.postRunnable(() -> navigator.showMainMenu(serverUrl, username)));
        SendInputUseCase sendInput = new SendInputUseCase(session);

        PreferencesPort prefs = new PreferencesRepository();

        inputHandler  = new InputHandler(camera);
        controller    = new GameController(worldState, inputHandler, sendInput);
        worldRenderer = new WorldRenderer(worldState, camera, shapes, batch);
        hudRenderer   = new HudRenderer(worldState, inputHandler, shapes, batch, font, bigFont, prefs);

        // --- Background Music ---
        try {
            com.badlogic.gdx.files.FileHandle musicFile = Gdx.files.internal("Boney_M_Rasputin.ogg");
            if (musicFile.exists()) {
                backgroundMusic = Gdx.audio.newMusic(musicFile);
                backgroundMusic.setLooping(true);
                backgroundMusic.setVolume(0.5f);
                if (prefs.isSoundEnabled()) {
                    backgroundMusic.play();
                }
            } else {
                System.err.println("[AUDIO] Music file not found: Boney_M_Rasputin.ogg");
            }
        } catch (Exception e) {
            System.err.println("[AUDIO] Error loading music: " + e.getMessage());
        }

        session.connect(serverUrl, username);
    }

    @Override
    public void render(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            navigator.showMainMenu(serverUrl, username);
            return;
        }

        controller.update(delta);
        worldRenderer.updateCamera();

        Gdx.gl.glClearColor(0.08f, 0.08f, 0.10f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        worldRenderer.render();
        hudRenderer.render();
    }

    @Override
    public void resize(int width, int height) {
        if (hudRenderer != null) hudRenderer.resize();
    }

    @Override public void pause()  {}
    @Override public void resume() {}

    @Override
    public void hide() {
        if (inputHandler != null) inputHandler.clearInputProcessor();
        if (backgroundMusic != null) backgroundMusic.stop();
    }

    @Override
    public void dispose() {
        if (session       != null) session.disconnect();
        if (worldRenderer != null) worldRenderer.dispose();
        if (shapes        != null) shapes.dispose();
        if (batch         != null) batch.dispose();
        if (font          != null) font.dispose();
        if (bigFont       != null) bigFont.dispose();
        if (backgroundMusic != null) backgroundMusic.dispose();
    }
}
