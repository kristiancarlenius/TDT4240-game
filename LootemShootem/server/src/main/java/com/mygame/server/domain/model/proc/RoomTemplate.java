package com.mygame.server.domain.model.proc;

import com.mygame.shared.dto.TileType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RoomTemplate {

    public final String id;
    public final RoomKind kind;
    public final int width;
    public final int height;
    public final TileType[] tiles;
    // Local tile coordinates (x,y) where chest spawns are allowed inside this template.
    public final List<int[]> chestSpawnTiles;

    public RoomTemplate(
            String id,
            RoomKind kind,
            int width,
            int height,
            TileType[] tiles,
            List<int[]> chestSpawnTiles) {
        this.id = id;
        this.kind = kind;
        this.width = width;
        this.height = height;
        this.tiles = tiles;
        List<int[]> copy = new ArrayList<>();
        for (int[] p : chestSpawnTiles) {
            copy.add(new int[]{p[0], p[1]});
        }
        this.chestSpawnTiles = Collections.unmodifiableList(copy);
    }
}
