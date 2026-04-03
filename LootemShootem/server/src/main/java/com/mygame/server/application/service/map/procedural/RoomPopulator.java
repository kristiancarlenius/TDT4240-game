package com.mygame.server.application.service.map.procedural;

import com.mygame.server.domain.model.proc.*;
import com.mygame.server.domain.ports.map.RoomTemplateCatalogPort;
import com.mygame.shared.dto.TileType;

import java.util.*;

public final class RoomPopulator {

    private final RoomTemplateCatalogPort catalog;
    private final RoomGenerator roomGenerator;

    public RoomPopulator(RoomTemplateCatalogPort catalog, RoomGenerator roomGenerator) {
        this.catalog = catalog;
        this.roomGenerator = roomGenerator;
    }

    public void populate(MapGraph graph, TileType[] worldTiles, int worldWidth, Random rng) {
        for (RoomNode room : graph.rooms()) {
            List<RoomTemplate> templates = catalog.templatesFor(room.kind);
            RoomGenerationContext ctx = new RoomGenerationContext(room, graph.incidentEdges(room.id), rng);
            RoomSelection selection = roomGenerator.select(ctx, templates);

            room.templateId = selection.templateId;
            room.transform = selection.transform;

            RoomTemplate template = byId(templates, selection.templateId);
            stampTemplate(room, template, selection.transform, worldTiles, worldWidth);
        }
    }

    private static RoomTemplate byId(List<RoomTemplate> templates, String id) {
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
            int worldWidth) {

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
