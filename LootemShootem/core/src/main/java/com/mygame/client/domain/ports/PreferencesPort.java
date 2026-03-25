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
    boolean isControlsSwapped();
    void setControlsSwapped(boolean swapped);
}
