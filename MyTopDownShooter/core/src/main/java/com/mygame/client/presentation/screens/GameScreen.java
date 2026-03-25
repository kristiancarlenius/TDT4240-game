package com.mygame.client.presentation.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.mygame.client.application.service.GameSessionService;
import com.mygame.client.application.usecase.ApplySnapshotUseCase;
import com.mygame.client.application.usecase.SendInputUseCase;
import com.mygame.client.domain.model.WorldState;
import com.mygame.client.presentation.controller.GameController;
import com.mygame.client.presentation.navigation.Navigator;
import com.mygame.client.presentation.view.input.InputHandler;
import com.mygame.client.presentation.view.renderer.HudRenderer;
import com.mygame.client.presentation.view.renderer.WorldRenderer;

/**
 * Thin LibGDX Screen: creates and wires the MVC components, then delegates to
 * them each frame. Contains no game logic or rendering code itself.
 *
 * Wiring order (all inside show()):
 *   WorldState → ApplySnapshotUseCase
 *              → GameSessionService (Facade / NetListener)
 *              → SendInputUseCase
 *   InputHandler (registers touch processor on Android)
 *   GameController (input → SendInputUseCase at 20 Hz)
 *   WorldRenderer  (world-space drawing)
 *   HudRenderer    (screen-space HUD drawing)
 */
public final class GameScreen implements Screen {

    private final Navigator navigator;
    private final String    serverUrl;
    private final String    username;

    // MVC components — initialised in show()
    private GameSessionService session;
    private GameController     controller;
    private WorldRenderer      worldRenderer;
    private HudRenderer        hudRenderer;
    private InputHandler       inputHandler;

    // LibGDX resources — disposed in dispose()
    private OrthographicCamera camera;
    private ShapeRenderer      shapes;
    private SpriteBatch        batch;
    private BitmapFont         font;
    private BitmapFont         bigFont;

    public GameScreen(Navigator navigator, String serverUrl, String username) {
        this.navigator = navigator;
        this.serverUrl = serverUrl;
        this.username  = username;
    }

    // ---- Screen lifecycle ----

    @Override
    public void show() {
        // 1 — LibGDX resources
        camera = new OrthographicCamera();
        camera.setToOrtho(false, 32f, 18f);
        camera.update();

        shapes  = new ShapeRenderer();
        batch   = new SpriteBatch();
        font    = new BitmapFont();
        font.getData().setScale(1.4f);
        bigFont = new BitmapFont();
        bigFont.getData().setScale(3.5f);

        // 2 — Domain model
        WorldState worldState = new WorldState();

        // 3 — Application layer
        ApplySnapshotUseCase applySnapshot = new ApplySnapshotUseCase(worldState);
        session = new GameSessionService(
                worldState,
                applySnapshot,
                () -> Gdx.app.postRunnable(() -> navigator.showMainMenu(serverUrl, username)));
        SendInputUseCase sendInput = new SendInputUseCase(session);

        // 4 — Input handler (registers InputProcessor on Android)
        inputHandler = new InputHandler(camera);

        // 5 — Controller
        controller = new GameController(worldState, inputHandler, sendInput);

        // 6 — Renderers
        worldRenderer = new WorldRenderer(worldState, camera, shapes);
        hudRenderer   = new HudRenderer(worldState, inputHandler, shapes, batch, font, bigFont);

        // 7 — Connect to server
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
    }

    @Override
    public void dispose() {
        if (session != null) session.disconnect();
        if (shapes  != null) shapes.dispose();
        if (batch   != null) batch.dispose();
        if (font    != null) font.dispose();
        if (bigFont != null) bigFont.dispose();
    }
}
