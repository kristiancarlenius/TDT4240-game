package com.mygame.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.mygame.client.GameClientApp;

public final class DesktopLauncher {
    public static void main(String[] args) {
        String url = System.getenv().getOrDefault("SERVER_URL", "ws://localhost:8080/ws");
        String user = System.getenv().getOrDefault("USERNAME", "player_" + (int)(Math.random() * 1000));

        Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
        cfg.setTitle("TopDownShooter MVP");
        cfg.setWindowedMode(1280, 720);
        cfg.setForegroundFPS(60);

        new Lwjgl3Application(new GameClientApp(url, user), cfg);
    }
}