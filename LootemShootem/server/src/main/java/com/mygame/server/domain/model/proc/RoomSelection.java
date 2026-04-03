package com.mygame.server.domain.model.proc;

public final class RoomSelection {

    public final String templateId;
    public final RoomTransform transform;

    public RoomSelection(String templateId, RoomTransform transform) {
        this.templateId = templateId;
        this.transform = transform;
    }
}
