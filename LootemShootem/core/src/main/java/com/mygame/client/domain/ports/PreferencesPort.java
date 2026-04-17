package com.mygame.client.domain.ports;

import com.mygame.client.domain.model.HudSlot;
import com.mygame.client.domain.model.HudWidget;

public interface PreferencesPort {
    String    getUsername();
    void      saveUsername(String username);

    HudWidget getHudWidget(HudSlot slot);
    void      saveHudWidget(HudSlot slot, HudWidget widget);

    boolean isSoundEnabled();
    void setSoundEnabled(boolean enabled);

    boolean isMusicEnabled();
    void setMusicEnabled(boolean enabled);
    float getMusicVolume();
    void setMusicVolume(float volume);

    boolean isControlsSwapped();
    void setControlsSwapped(boolean swapped);

    float getTouchJoystickScale();
    void setTouchJoystickScale(float scale);

    float getTouchJoystickOpacity();
    void setTouchJoystickOpacity(float opacity);

    /** Skin index (0–3) chosen by the player. */
    int  getSkinId();
    void setSkinId(int skinId);
}
