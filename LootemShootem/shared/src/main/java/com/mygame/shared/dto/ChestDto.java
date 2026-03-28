package com.mygame.shared.dto;

import com.mygame.shared.util.Vec2;

/** Wire-safe snapshot of a chest on the map. */
public final class ChestDto {
    public String  chestId;
    public Vec2    pos;
    public boolean isOpen;

    public ChestDto() {}

    public ChestDto(String chestId, Vec2 pos, boolean isOpen) {
        this.chestId = chestId;
        this.pos     = pos;
        this.isOpen  = isOpen;
    }
}
