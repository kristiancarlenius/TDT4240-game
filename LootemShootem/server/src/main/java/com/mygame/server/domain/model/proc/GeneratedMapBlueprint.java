package com.mygame.server.domain.model.proc;

import com.mygame.shared.dto.TileType;

public final class GeneratedMapBlueprint {

    public final MapGenerationSpec spec;
    public final MapGraph graph;

    // World-space tile array including outer 1-tile border.
    public final int worldWidth;
    public final int worldHeight;
    public final TileType[] tiles;

    public GeneratedMapBlueprint(MapGenerationSpec spec, MapGraph graph, int worldWidth, int worldHeight, TileType[] tiles) {
        this.spec = spec;
        this.graph = graph;
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.tiles = tiles;
    }
}
