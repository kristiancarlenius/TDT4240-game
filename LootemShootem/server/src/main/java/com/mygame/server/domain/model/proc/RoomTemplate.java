package com.mygame.server.domain.model.proc;

import com.mygame.shared.dto.TileType;

public final class RoomTemplate {

    public final String id;
    public final RoomKind kind;
    public final int width;
    public final int height;
    public final TileType[] tiles;

    public RoomTemplate(String id, RoomKind kind, int width, int height, TileType[] tiles) {
        this.id = id;
        this.kind = kind;
        this.width = width;
        this.height = height;
        this.tiles = tiles;
    }
}
