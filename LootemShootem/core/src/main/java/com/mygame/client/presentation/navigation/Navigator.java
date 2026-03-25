package com.mygame.client.presentation.navigation;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Screen;
import com.mygame.client.presentation.screens.GameScreen;
import com.mygame.client.presentation.screens.MainMenuScreen;
import com.mygame.client.presentation.screens.SettingsScreen;

/**
 * Central navigation controller. All screen transitions go through here so
 * that no screen holds a direct reference to another screen.
 */
public final class Navigator {

    private final Game game;

    public Navigator(Game game) {
        this.game = game;
    }

    public void showMainMenu(String serverUrl, String defaultUsername) {
        switchTo(new MainMenuScreen(this, serverUrl, defaultUsername));
    }

    public void showGame(String serverUrl, String username) {
        switchTo(new GameScreen(this, serverUrl, username));
    }

    public void showSettings(String serverUrl, String username) {
        switchTo(new SettingsScreen(this, serverUrl, username));
    }

    /** Sets the new screen and disposes the previous one. */
    private void switchTo(Screen next) {
        Screen previous = game.getScreen();
        game.setScreen(next);
        if (previous != null) previous.dispose();
    }
}
