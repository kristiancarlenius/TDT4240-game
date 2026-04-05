package com.mygame.server.application.service.map;

import com.mygame.server.application.service.map.procedural.*;
import com.mygame.server.domain.model.proc.*;
import com.mygame.shared.dto.TileType;
import com.mygame.shared.util.Vec2;

import java.util.*;

/**
 * Application service layer orchestrator for procedural map generation.
 * Responsible for coordinating the multi-stage map generation pipeline:
 * 1. Build skeleton room graph (gridded dungeon layout)
 * 2. Assign border states (doors, walls, windows, open passage)
 * 3. Carve tilemap from the graph structure
 * 4. Populate rooms with tile templates
 * Produces a complete GeneratedMapBlueprint from a specification and seed.
 */
public final class ProceduralMapController {

    private final MapGenerationSpec spec;
    private final SkeletonGraphBuilder graphBuilder;
    private final BorderStateAssigner borderStateAssigner;
    private final RoomPopulator roomPopulator;

    /**
     * Constructs a procedural map controller with the given generation components.
     *
     * @param spec map generation specification (room size, spacing, central room bounds)
     * @param graphBuilder builds the dungeon room layout graph
     * @param borderStateAssigner assigns door/wall states to room boundaries
     * @param roomPopulator stamps room templates into the tilemap
     */
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

    /**
     * Generates a complete map blueprint from the spec and seed.
     * Pipeline:
     * 1. Build graph: creates room nodes and border edges from the spec
     * 2. Assign borders: deterministically assigns door/wall states to edges
     * 3. Draw skeleton: carves vertical and horizontal walls separating room slots
     * 4. Carve central: clears central authority room
     * 5. Apply states: creates doors/windows/passages based on assigned border states
     * 6. Populate rooms: stamps room templates with variations (mirrors) and collects chest spawns
     *
     * @param mapId unique map identifier
     * @param seed seed for all RNG operations
     * @return complete map blueprint with tilemap and metadata
     */
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

        List<Vec2> chestSpawnPoints = roomPopulator.populate(graph, worldTiles, worldWidth, rng);

        return new GeneratedMapBlueprint(spec, graph, worldWidth, worldHeight, worldTiles, chestSpawnPoints);
    }

    // Stage 1: Draw the outer border (1-tile perimeter) as solid walls.
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

    // Stage 2: Draw the skeleton grid walls. These separate the playable area into room slots.
    // Vertical walls at x = skeletonSpacingX - 1, then every skeletonSpacingX pixels.
    // Horizontal walls at y = skeletonSpacingY - 1, then every skeletonSpacingY pixels.
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

    // Stage 3: Carve out the central room (boss arena) by clearing its area to FLOOR.
    private static void carveCentralRoom(TileType[] tiles, int worldWidth, MapGenerationSpec spec) {
        int maxX = spec.centralStartX + spec.centralWidth - 1;
        int maxY = spec.centralStartY + spec.centralHeight - 1;
        for (int y = spec.centralStartY; y <= maxY; y++) {
            for (int x = spec.centralStartX; x <= maxX; x++) {
                setPlayableTile(tiles, worldWidth, x, y, TileType.FLOOR);
            }
        }
    }

    // Stage 4: Apply border states to create doors, windows, or open passages between rooms.
    // BorderState.NONE = fully open (no walls), BorderState.DOOR = floor with passage,
    // BorderState.WINDOW = visual-only barrier, BorderState.WALL = remains solid.
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

    // Carves an entire border edge to FLOOR (BorderState.NONE).
    private static void carveEntireBorder(TileType[] tiles, int worldWidth, BorderEdge edge) {
        for (int p = edge.spanStart; p <= edge.spanEnd; p++) {
            if (edge.orientation == BorderOrientation.VERTICAL) {
                setPlayableTile(tiles, worldWidth, edge.lineCoord, p, TileType.FLOOR);
            } else {
                setPlayableTile(tiles, worldWidth, p, edge.lineCoord, TileType.FLOOR);
            }
        }
    }

    // Converts playable-space coordinates to world-space (accounting for 1-tile border offset) and sets tile.
    private static void setPlayableTile(TileType[] tiles, int worldWidth, int playableX, int playableY, TileType tile) {
        int worldX = playableX + 1;
        int worldY = playableY + 1;
        tiles[worldY * worldWidth + worldX] = tile;
    }
}
