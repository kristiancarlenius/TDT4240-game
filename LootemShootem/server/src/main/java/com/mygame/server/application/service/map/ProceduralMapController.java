package com.mygame.server.application.service.map;

import com.mygame.server.application.service.map.procedural.*;
import com.mygame.server.domain.model.proc.*;
import com.mygame.shared.dto.TileType;

import java.util.*;

public final class ProceduralMapController {

    private final MapGenerationSpec spec;
    private final SkeletonGraphBuilder graphBuilder;
    private final BorderStateAssigner borderStateAssigner;
    private final RoomPopulator roomPopulator;

    public ProceduralMapController(
            MapGenerationSpec spec,
            SkeletonGraphBuilder graphBuilder,
            BorderStateAssigner borderStateAssigner,
            RoomPopulator roomPopulator) {
        this.spec = spec;
        this.graphBuilder = graphBuilder;
        this.borderStateAssigner = borderStateAssigner;
        this.roomPopulator = roomPopulator;
    }

    public GeneratedMapBlueprint generate(String mapId, long seed) {
        Random rng = new Random(seed);

        MapGraph graph = graphBuilder.build(spec);
        borderStateAssigner.assign(graph, rng, spec);

        int worldWidth = spec.worldWidth();
        int worldHeight = spec.worldHeight();
        TileType[] worldTiles = new TileType[worldWidth * worldHeight];
        Arrays.fill(worldTiles, TileType.FLOOR);

        drawOuterBorder(worldTiles, worldWidth, worldHeight);
        drawSkeletonWalls(worldTiles, worldWidth, spec);
        carveCentralRoom(worldTiles, worldWidth, spec);
        applyBorderStates(worldTiles, worldWidth, graph, spec);

        roomPopulator.populate(graph, worldTiles, worldWidth, rng);

        return new GeneratedMapBlueprint(spec, graph, worldWidth, worldHeight, worldTiles);
    }

    private static void drawOuterBorder(TileType[] tiles, int worldWidth, int worldHeight) {
        for (int x = 0; x < worldWidth; x++) {
            tiles[x] = TileType.WALL;
            tiles[(worldHeight - 1) * worldWidth + x] = TileType.WALL;
        }
        for (int y = 0; y < worldHeight; y++) {
            tiles[y * worldWidth] = TileType.WALL;
            tiles[y * worldWidth + (worldWidth - 1)] = TileType.WALL;
        }
    }

    private static void drawSkeletonWalls(TileType[] tiles, int worldWidth, MapGenerationSpec spec) {
        for (int x = spec.skeletonSpacingX - 1; x < spec.playableWidth; x += spec.skeletonSpacingX) {
            for (int y = 0; y < spec.playableHeight; y++) {
                setPlayableTile(tiles, worldWidth, x, y, TileType.WALL);
            }
        }
        for (int y = spec.skeletonSpacingY - 1; y < spec.playableHeight; y += spec.skeletonSpacingY) {
            for (int x = 0; x < spec.playableWidth; x++) {
                setPlayableTile(tiles, worldWidth, x, y, TileType.WALL);
            }
        }
    }

    private static void carveCentralRoom(TileType[] tiles, int worldWidth, MapGenerationSpec spec) {
        int maxX = spec.centralStartX + spec.centralWidth - 1;
        int maxY = spec.centralStartY + spec.centralHeight - 1;
        for (int y = spec.centralStartY; y <= maxY; y++) {
            for (int x = spec.centralStartX; x <= maxX; x++) {
                setPlayableTile(tiles, worldWidth, x, y, TileType.FLOOR);
            }
        }
    }

    private static void applyBorderStates(TileType[] tiles, int worldWidth, MapGraph graph, MapGenerationSpec spec) {
        for (BorderEdge edge : graph.edges()) {
            if (edge.state == BorderState.NONE) {
                carveEntireBorder(tiles, worldWidth, edge);
                continue;
            }
            if (edge.state == BorderState.WALL) {
                continue;
            }

            int openingLength = edge.orientation == BorderOrientation.HORIZONTAL ? 3 : 2;
            int center = (edge.spanStart + edge.spanEnd) / 2;
            int start = Math.max(edge.spanStart, center - ((openingLength - 1) / 2));
            int end = Math.min(edge.spanEnd, start + openingLength - 1);

            for (int p = start; p <= end; p++) {
                TileType tile = edge.state == BorderState.WINDOW ? TileType.WINDOW : TileType.FLOOR;
                if (edge.orientation == BorderOrientation.VERTICAL) {
                    setPlayableTile(tiles, worldWidth, edge.lineCoord, p, tile);
                } else {
                    setPlayableTile(tiles, worldWidth, p, edge.lineCoord, tile);
                }
            }
        }
    }

    private static void carveEntireBorder(TileType[] tiles, int worldWidth, BorderEdge edge) {
        for (int p = edge.spanStart; p <= edge.spanEnd; p++) {
            if (edge.orientation == BorderOrientation.VERTICAL) {
                setPlayableTile(tiles, worldWidth, edge.lineCoord, p, TileType.FLOOR);
            } else {
                setPlayableTile(tiles, worldWidth, p, edge.lineCoord, TileType.FLOOR);
            }
        }
    }

    private static void setPlayableTile(TileType[] tiles, int worldWidth, int playableX, int playableY, TileType tile) {
        int worldX = playableX + 1;
        int worldY = playableY + 1;
        tiles[worldY * worldWidth + worldX] = tile;
    }
}
