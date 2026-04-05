package com.mygame.server.application.service.map.procedural;

import com.mygame.server.domain.model.proc.*;
import com.mygame.server.domain.ports.map.RoomTemplateCatalogPort;
import com.mygame.shared.dto.TileType;
import com.mygame.shared.util.Vec2;

import java.util.*;

/**
 * Populates a tilemap by stamping room templates into their designated slots.
 * For each room in the graph:
 * 1. Selects a template variant matching the room dimensions
 * 2. Stamps the template tiles into the world at the room's position
 * 3. Collects chest spawn points from the template, transforming them to world coordinates
 */
public final class RoomPopulator {

    private final RoomTemplateCatalogPort catalog;
    private final RoomGenerator roomGenerator;

    public RoomPopulator(RoomTemplateCatalogPort catalog, RoomGenerator roomGenerator) {
        this.catalog = catalog;
        this.roomGenerator = roomGenerator;
    }

    public List<Vec2> populate(MapGraph graph, TileType[] worldTiles, int worldWidth, Random rng) {
        List<Vec2> chestSpawnPoints = new ArrayList<>();
        for (RoomNode room : graph.rooms()) {
            List<RoomTemplate> templates = catalog.templatesFor(room.kind);
            RoomGenerationContext ctx = new RoomGenerationContext(room, graph.incidentEdges(room.id), rng);
            RoomSelection selection = roomGenerator.select(ctx, templates);

            room.templateId = selection.templateId;
            room.transform = selection.transform;

            RoomTemplate template = byId(templates, selection.templateId);
            stampTemplate(room, template, selection.transform, worldTiles, worldWidth, chestSpawnPoints);
        }
        return chestSpawnPoints;
    }

    private static RoomTemplate byId(List<RoomTemplate> templates, String id) {
        // Finds a template by ID in the candidate list.
        for (RoomTemplate t : templates) {
            if (t.id.equals(id)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Template not found: " + id);
    }

    private static void stampTemplate(
            RoomNode room,
            RoomTemplate template,
            RoomTransform transform,
            TileType[] worldTiles,
            int worldWidth,
            List<Vec2> chestSpawnPoints) {

        int roomWidth = room.width();
        int roomHeight = room.height();
        int[] transformed = transformedSize(template.width, template.height, transform);
        if (transformed[0] != roomWidth || transformed[1] != roomHeight) {
            throw new IllegalArgumentException(
                    "Template dimensions do not match room dimensions for room=" + room.id
                            + " template=" + template.id);
        }

        for (int y = 0; y < roomHeight; y++) {
            for (int x = 0; x < roomWidth; x++) {
                int[] source = sourceCoordFor(x, y, template.width, template.height, transform);
                TileType tile = template.tiles[source[1] * template.width + source[0]];

                int worldX = room.minX + x + 1;
                int worldY = room.minY + y + 1;
                worldTiles[worldY * worldWidth + worldX] = tile;
            }
        }

        for (int[] local : template.chestSpawnTiles) {
            int[] source = sourceCoordFor(local[0], local[1], template.width, template.height, transform);
            int worldX = room.minX + source[0] + 1;
            int worldY = room.minY + source[1] + 1;
            chestSpawnPoints.add(new Vec2(worldX + 0.5f, worldY + 0.5f));
        }
    }

    private static int[] transformedSize(int width, int height, RoomTransform transform) {
        // Mirrors preserve dimensions; no rotations
        return new int[]{width, height};
    }

    private static int[] sourceCoordFor(int x, int y, int width, int height, RoomTransform transform) {
        switch (transform) {
            case ROT_0:
                return new int[]{x, y};
            case MIRROR_X:
                return new int[]{width - 1 - x, y};
            case MIRROR_Y:
                return new int[]{x, height - 1 - y};
            default:
                return new int[]{x, y};
        }
    }
}
