package com.mygame.client.data.repository;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.mygame.client.domain.model.HudSlot;
import com.mygame.client.domain.model.HudWidget;
import com.mygame.client.domain.ports.PreferencesPort;

public final class PreferencesRepository implements PreferencesPort {

    private static final String PREFS_NAME         = "lootem";
    private static final String KEY_USERNAME        = "username";
    private static final String KEY_HUD             = "hud_slot_";
    private static final String KEY_SOUND_ENABLED   = "sound_enabled";
    private static final String KEY_MUSIC_ENABLED   = "music_enabled";
    private static final String KEY_MUSIC_VOLUME    = "music_volume";
    private static final String KEY_CONTROLS_SWAPPED = "controls_swapped";
    private static final String KEY_TOUCH_JOYSTICK_SCALE = "touch_joystick_scale";
    private static final String KEY_TOUCH_JOYSTICK_OPACITY = "touch_joystick_opacity";
    private static final String KEY_SKIN_ID          = "skin_id";

    /** Default widget for each slot ordinal: LEFT→LEADERBOARD, CENTER→TIME_ALIVE, RIGHT→MINIMAP */
    private static final HudWidget[] HUD_DEFAULTS = {
            HudWidget.LEADERBOARD,
            HudWidget.TIME_ALIVE,
            HudWidget.MINIMAP
    };

    private final Preferences prefs = Gdx.app.getPreferences(PREFS_NAME);

    public PreferencesRepository() {
        // prefs field is initialised inline above; constructor kept for explicit instantiation
    }

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
    public HudWidget getHudWidget(HudSlot slot) {
        String stored = prefs.getString(KEY_HUD + slot.ordinal(),
                HUD_DEFAULTS[slot.ordinal()].name());
        try {
            return HudWidget.valueOf(stored);
        } catch (Exception e) {
            return HUD_DEFAULTS[slot.ordinal()];
        }
    }

    @Override
    public void saveHudWidget(HudSlot slot, HudWidget widget) {
        prefs.putString(KEY_HUD + slot.ordinal(), widget.name());
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
    public boolean isMusicEnabled() {
        return prefs.getBoolean(KEY_MUSIC_ENABLED, true);
    }

    @Override
    public void setMusicEnabled(boolean enabled) {
        prefs.putBoolean(KEY_MUSIC_ENABLED, enabled);
        prefs.flush();
    }

    @Override
    public float getMusicVolume() {
        return prefs.getFloat(KEY_MUSIC_VOLUME, 0.5f);
    }

    @Override
    public void setMusicVolume(float volume) {
        prefs.putFloat(KEY_MUSIC_VOLUME, Math.max(0f, Math.min(1f, volume)));
        prefs.flush();
    }

    @Override
    public boolean isControlsSwapped() {
        return prefs.getBoolean(KEY_CONTROLS_SWAPPED, false);
    }

    @Override
    public void setControlsSwapped(boolean swapped) {
        prefs.putBoolean(KEY_CONTROLS_SWAPPED, swapped);
        prefs.flush();
    }

    @Override
    public float getTouchJoystickScale() {
        return prefs.getFloat(KEY_TOUCH_JOYSTICK_SCALE, 1.0f);
    }

    @Override
    public void setTouchJoystickScale(float scale) {
        prefs.putFloat(KEY_TOUCH_JOYSTICK_SCALE, Math.max(0.75f, Math.min(1.45f, scale)));
        prefs.flush();
    }

    @Override
    public float getTouchJoystickOpacity() {
        return prefs.getFloat(KEY_TOUCH_JOYSTICK_OPACITY, 0.78f);
    }

    @Override
    public void setTouchJoystickOpacity(float opacity) {
        prefs.putFloat(KEY_TOUCH_JOYSTICK_OPACITY, Math.max(0.25f, Math.min(1.0f, opacity)));
        prefs.flush();
    }

    @Override
    public int getSkinId() {
        return prefs.getInteger(KEY_SKIN_ID, 0);
    }

    @Override
    public void setSkinId(int skinId) {
        prefs.putInteger(KEY_SKIN_ID, Math.max(0, Math.min(3, skinId)));
        prefs.flush();
    }
}
