package com.mygame.server.domain.model.proc;

import com.mygame.shared.dto.TileType;
import com.mygame.shared.util.Vec2;

import java.util.Collections;
import java.util.List;

public final class GeneratedMapBlueprint {

    public final MapGenerationSpec spec;
    public final MapGraph graph;

    // World-space tile array including outer 1-tile border.
    public final int worldWidth;
    public final int worldHeight;
    public final TileType[] tiles;
    public final List<Vec2> chestSpawnPoints;

    public GeneratedMapBlueprint(
            MapGenerationSpec spec,
            MapGraph graph,
            int worldWidth,
            int worldHeight,
            TileType[] tiles,
            List<Vec2> chestSpawnPoints) {
        this.spec = spec;
        this.graph = graph;
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.tiles = tiles;
        this.chestSpawnPoints = Collections.unmodifiableList(chestSpawnPoints);
    }
}
