package com.mygame.server.data.map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mygame.server.domain.model.ServerGameState;
import com.mygame.shared.dto.TileType;

import java.io.InputStream;
import java.util.Arrays;

/**
 * Parses a map JSON file from the classpath into a {@link ServerGameState}.
 *
 * JSON format:
 * <pre>
 * {
 *   "id": "map01",
 *   "width": 30,
 *   "height": 20,
 *   "rows": [
 *     "WWWWWW...",   <- visual top row  → world y = height-1
 *     ...
 *     "WWWWWW..."    <- visual bottom   → world y = 0
 *   ]
 * }
 * </pre>
 * Characters: W=WALL, F=FLOOR, T=TRAP, N=WINDOW.
 * Unknown chars fall back to FLOOR.
 */
public final class MapParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Loads the map from a classpath resource (e.g. "/maps/map01.json").
     */
    public static ServerGameState load(String classpathResource) {
        // Strip leading slash — ClassLoader.getResourceAsStream uses no leading slash
        String path = classpathResource.startsWith("/") ? classpathResource.substring(1) : classpathResource;
        try (InputStream in = MapParser.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("Map resource not found on classpath: " + path);
            }
            return parse(MAPPER.readTree(in));
        } catch (Exception e) {
            throw new RuntimeException("Failed to load map: " + classpathResource, e);
        }
    }

    private static ServerGameState parse(JsonNode root) {
        String id     = root.get("id").asText();
        int    width  = root.get("width").asInt();
        int    height = root.get("height").asInt();

        TileType[] tiles = new TileType[width * height];
        Arrays.fill(tiles, TileType.FLOOR);

        JsonNode rows = root.get("rows");
        if (rows == null || !rows.isArray()) {
            throw new IllegalArgumentException("Map JSON missing 'rows' array");
        }
        if (rows.size() != height) {
            throw new IllegalArgumentException(
                    "Map height mismatch: expected " + height + " rows, got " + rows.size());
        }

        // rows[0] is the visual top → world y = height-1
        for (int rowIdx = 0; rowIdx < rows.size(); rowIdx++) {
            String row = rows.get(rowIdx).asText();
            if (row.length() != width) {
                throw new IllegalArgumentException(
                        "Row " + rowIdx + " has length " + row.length() + ", expected " + width);
            }
            int worldY = height - 1 - rowIdx;
            for (int x = 0; x < width; x++) {
                tiles[worldY * width + x] = charToTile(row.charAt(x));
            }
        }

        return ServerGameState.fromTiles(id, width, height, tiles);
    }

    private static TileType charToTile(char c) {
        switch (c) {
            case 'W': return TileType.WALL;
            case 'T': return TileType.TRAP;
            case 'N': return TileType.WINDOW;
            default:  return TileType.FLOOR;
        }
    }
}
