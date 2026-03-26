package com.mygame.client.domain.ports;

public interface PreferencesPort {
    boolean isSoundEnabled();
    void setSoundEnabled(boolean enabled);
    boolean isControlsSwapped();
    void setControlsSwapped(boolean swapped);
    String getUsername();
    void saveUsername(String username);
}
