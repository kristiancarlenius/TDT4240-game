package com.mygame.server.domain.model;

import com.mygame.shared.dto.MapDto;
import com.mygame.shared.dto.PickupDto;
import com.mygame.shared.dto.ProjectileDto;
import com.mygame.shared.dto.TileType;
import com.mygame.shared.util.Vec2;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerGameState {

    public final String mapId;
    public final int width;
    public final int height;

    // row-major tiles[y*width + x]
    public final TileType[] tiles;

    public long tick = 0;

    public final Map<String, PlayerState> players = new ConcurrentHashMap<>();
    public final List<ProjectileDto> projectiles = Collections.synchronizedList(new ArrayList<>());
    public final List<PickupDto> pickups = Collections.synchronizedList(new ArrayList<>());

    private int spawnIndex = 0;

    private ServerGameState(String mapId, int width, int height, TileType[] tiles) {
        this.mapId = mapId;
        this.width = width;
        this.height = height;
        this.tiles = tiles;
    }

    public static ServerGameState createWithBorderWalls(String mapId, int width, int height) {
        TileType[] t = new TileType[width * height];
        Arrays.fill(t, TileType.FLOOR);

        // border walls
        for (int x = 0; x < width; x++) {
            t[idx(x, 0, width)] = TileType.WALL;
            t[idx(x, height - 1, width)] = TileType.WALL;
        }
        for (int y = 0; y < height; y++) {
            t[idx(0, y, width)] = TileType.WALL;
            t[idx(width - 1, y, width)] = TileType.WALL;
        }

        // a couple obstacles (example)
        for (int x = 10; x <= 18; x++) {
            t[idx(x, 8, width)] = TileType.WALL;
        }
        t[idx(14, 9, width)] = TileType.TRAP;

        return new ServerGameState(mapId, width, height, t);
    }

    public MapDto toMapDto() {
        // Note: sending full tile array is fine for MVP; later you can send mapId+seed.
        return new MapDto(mapId, width, height, tiles);
    }

    public Vec2 findNextSpawn() {
        // A few fixed spawns (world coords are tile centers)
        Vec2[] spawns = new Vec2[] {
                new Vec2(2.5f, 2.5f),
                new Vec2(width - 3.5f, 2.5f),
                new Vec2(2.5f, height - 3.5f),
                new Vec2(width - 3.5f, height - 3.5f),
                new Vec2(width / 2f, height / 2f)
        };
        Vec2 s = spawns[spawnIndex % spawns.length];
        spawnIndex++;
        return s;
    }

    /** Very simple collision: treat player as a point and forbid entering non-walkable tiles. */
    public Vec2 collidePlayer(Vec2 oldPos, Vec2 desiredPos) {
        // attempt full move; if blocked, try axis-separated
        if (isWalkableWorld(desiredPos.x, desiredPos.y)) return desiredPos;

        Vec2 tryX = new Vec2(desiredPos.x, oldPos.y);
        if (isWalkableWorld(tryX.x, tryX.y)) return tryX;

        Vec2 tryY = new Vec2(oldPos.x, desiredPos.y);
        if (isWalkableWorld(tryY.x, tryY.y)) return tryY;

        return oldPos;
    }

    public boolean isWalkableWorld(float wx, float wy) {
        int tx = (int)Math.floor(wx);
        int ty = (int)Math.floor(wy);
        if (!inBounds(tx, ty)) return false;

        TileType tt = tiles[idx(tx, ty, width)];
        // Walkable: FLOOR, TRAP
        return tt == TileType.FLOOR || tt == TileType.TRAP;
    }

    public boolean isTrapAtWorld(float wx, float wy) {
        int tx = (int)Math.floor(wx);
        int ty = (int)Math.floor(wy);
        if (!inBounds(tx, ty)) return false;
        return tiles[idx(tx, ty, width)] == TileType.TRAP;
    }

    private boolean inBounds(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    private static int idx(int x, int y, int width) {
        return y * width + x;
    }
}