package com.mygame.server.domain.model.proc;

public enum BorderState {
    WALL,
    DOOR,
    WINDOW,
    NONE;

    public boolean isTraversable() {
        return this == DOOR || this == NONE;
    }
}
