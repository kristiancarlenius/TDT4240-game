package com.mygame.client.data.repository;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.mygame.client.domain.ports.PreferencesPort;

public final class PreferencesRepository implements PreferencesPort {

    private static final String PREFS_NAME   = "lootem";
    private static final String KEY_USERNAME = "username";
          private static final String KEY_SOUND_ENABLED = "sound_enabled";
    private static final String KEY_CONTROLS_SWAPPED = "controls_swapped";

    // Preferences is backed by a file on desktop and SharedPreferences on Android
    private final Preferences prefs = Gdx.app.getPreferences(PREFS_NAME);

    @Override
    public String getUsername() {
        return prefs.getString(KEY_USERNAME, "");
    }

    @Override
    public void saveUsername(String username) {
        prefs.putString(KEY_USERNAME, username);
        prefs.flush();
    }
      @Override
    public boolean isSoundEnabled() {
        return prefs.getBoolean(KEY_SOUND_ENABLED, true);
    }

    @Override
    public void setSoundEnabled(boolean enabled) {
        prefs.putBoolean(KEY_SOUND_ENABLED, enabled);
        prefs.flush();
    }

    @Override
    public boolean isControlsSwapped() {
        return prefs.getBoolean(KEY_CONTROLS_SWAPPED, false);
    }

    @Override
    public void setControlsSwapped(boolean swapped) {
        prefs.putBoolean(KEY_CONTROLS_SWAPPED, swapped);
    }
}
