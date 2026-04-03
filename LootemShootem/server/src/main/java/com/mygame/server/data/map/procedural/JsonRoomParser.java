package com.mygame.server.data.map.procedural;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mygame.server.domain.model.proc.RoomKind;
import com.mygame.server.domain.model.proc.RoomTemplate;
import com.mygame.shared.dto.TileType;

import java.io.InputStream;
import java.util.Arrays;

public final class JsonRoomParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public RoomTemplate load(String classpathResource, RoomKind kind, String templateId) {
        String path = classpathResource.startsWith("/") ? classpathResource.substring(1) : classpathResource;
        try (InputStream in = JsonRoomParser.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("Room template not found: " + path);
            }
            JsonNode root = MAPPER.readTree(in);
            return parse(root, kind, templateId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load room template: " + classpathResource, e);
        }
    }

    private RoomTemplate parse(JsonNode root, RoomKind kind, String templateId) {
        int width = root.get("width").asInt();
        int height = root.get("height").asInt();
        JsonNode rows = root.get("rows");
        if (rows == null || !rows.isArray() || rows.size() != height) {
            throw new IllegalArgumentException("Room template rows mismatch for " + templateId);
        }

        TileType[] tiles = new TileType[width * height];
        Arrays.fill(tiles, TileType.FLOOR);

        for (int row = 0; row < rows.size(); row++) {
            String line = rows.get(row).asText();
            if (line.length() != width) {
                throw new IllegalArgumentException("Row width mismatch in " + templateId + " row=" + row);
            }
            int y = height - 1 - row;
            for (int x = 0; x < width; x++) {
                tiles[y * width + x] = charToTile(line.charAt(x));
            }
        }

        return new RoomTemplate(templateId, kind, width, height, tiles);
    }

    private static TileType charToTile(char c) {
        switch (c) {
            case 'W':
                return TileType.WALL;
            case 'N':
                return TileType.WINDOW;
            case 'T':
                return TileType.TRAP;
            case 'C':
                return TileType.COBWEB;
            default:
                return TileType.FLOOR;
        }
    }
}
