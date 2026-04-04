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
    public final List<Vec2> chestSpawnPoints;

    public long tick = 0;

    public final Map<String, PlayerState> players     = new ConcurrentHashMap<>();
    public final List<ProjectileState>   projectiles = Collections.synchronizedList(new ArrayList<>());
    public final List<PickupState>       pickups     = Collections.synchronizedList(new ArrayList<>());
    public final List<ChestState>        chests      = Collections.synchronizedList(new ArrayList<>());
    public final List<String>            killFeedQueue = Collections.synchronizedList(new ArrayList<>());

    private final Random spawnRng = new Random();
    private int spawnIndex = 0;

    private ServerGameState(String mapId, int width, int height, TileType[] tiles, List<Vec2> chestSpawnPoints) {
        this.mapId = mapId;
        this.width = width;
        this.height = height;
        this.tiles = tiles;
        this.chestSpawnPoints = Collections.unmodifiableList(new ArrayList<>(chestSpawnPoints));
    }

    /** Creates a state from a pre-built tile array (used by MapParser). */
    public static ServerGameState fromTiles(String mapId, int width, int height, TileType[] tiles) {
        return new ServerGameState(mapId, width, height, tiles, Collections.emptyList());
    }

    public static ServerGameState fromTiles(
            String mapId,
            int width,
            int height,
            TileType[] tiles,
            List<Vec2> chestSpawnPoints) {
        return new ServerGameState(mapId, width, height, tiles, chestSpawnPoints);
    }

    /** Fallback: programmatically generates a plain map with border walls + a few obstacles. */
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

        return new ServerGameState(mapId, width, height, t, Collections.emptyList());
    }

    public MapDto toMapDto() {
        // Note: sending full tile array is fine for MVP; later you can send mapId+seed.
        return new MapDto(mapId, width, height, tiles);
    }

    /**
     * Finds a safe spawn position on a FLOOR tile that is not occupied by
     * an alive player or a pickup.  Falls back to any floor tile if the map
     * is too crowded.
     */
    public Vec2 findSafeSpawn() {
        List<Vec2> candidates = new ArrayList<>();
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                if (tiles[idx(x, y, width)] == TileType.FLOOR) {
                    candidates.add(new Vec2(x + 0.5f, y + 0.5f));
                }
            }
        }

        // Remove tiles too close to alive players (1.5-tile safety radius)
        for (PlayerState p : players.values()) {
            if (!p.isDead && p.pos != null) {
                candidates.removeIf(c -> dist2(c, p.pos) < 2.25f);
            }
        }

        // Remove tiles occupied by a pickup
        for (PickupState pk : pickups) {
            if (pk.pos != null) {
                candidates.removeIf(c -> dist2(c, pk.pos) < 0.64f);
            }
        }

        if (candidates.isEmpty()) return randomFloorTile(spawnRng);
        return candidates.get(spawnRng.nextInt(candidates.size()));
    }

    /**
     * Cycles through a set of fixed corner/centre spawn points, skipping any
     * that land inside a wall tile.  Used by PlayerSystem on respawn so that
     * players never appear inside walls.
     */
    public Vec2 findNextSpawn() {
        // A few fixed spawns (world coords are tile centers)
        Vec2[] spawns = new Vec2[]{
                new Vec2(2.5f, 2.5f),
                new Vec2(width - 3.5f, 2.5f),
                new Vec2(2.5f, height - 3.5f),
                new Vec2(width - 3.5f, height - 3.5f),
                new Vec2(width / 2f, height / 2f)
        };

        // Select next walkable tile: ensure that player doesn't spawn inside wall
        for (int i = 0; i < spawns.length; i++) {
            int index = (spawnIndex + i) % spawns.length;
            Vec2 s = spawns[index];
            if (isWalkableWorld(s.x, s.y)) {
                spawnIndex = (index + 1) % spawns.length;
                return s;
            }
        }

        // Fallback: search for the first available floor tile
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (tiles[idx(x, y, width)] == TileType.FLOOR) {
                    return new Vec2(x + 0.5f, y + 0.5f);
                }
            }
        }
        return new Vec2(width / 2f, height / 2f);
    }

    private static float dist2(Vec2 a, Vec2 b) {
        float dx = a.x - b.x, dy = a.y - b.y;
        return dx * dx + dy * dy;
    }

    /** Returns the centre of a random floor tile (excluding border). */
    public Vec2 randomFloorTile(Random rng) {
        List<Vec2> floors = new ArrayList<>();
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                if (tiles[idx(x, y, width)] == TileType.FLOOR) {
                    floors.add(new Vec2(x + 0.5f, y + 0.5f));
                }
            }
        }
        if (floors.isEmpty()) return new Vec2(width / 2f, height / 2f);
        return floors.get(rng.nextInt(floors.size()));
    }

    /** Returns a random room-authored chest spawn point, falling back to any floor tile. */
    public Vec2 randomChestSpawnTile(Random rng) {
        List<Vec2> candidates = new ArrayList<>();
        for (Vec2 p : chestSpawnPoints) {
            if (isWalkableWorld(p.x, p.y)) {
                candidates.add(p);
            }
        }
        if (candidates.isEmpty()) {
            return randomFloorTile(rng);
        }
        return candidates.get(rng.nextInt(candidates.size()));
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
        return Tile.isWalkable(tiles[idx(tx, ty, width)]);
    }

    public boolean isTrapAtWorld(float wx, float wy) {
        int tx = (int)Math.floor(wx);
        int ty = (int)Math.floor(wy);
        if (!inBounds(tx, ty)) return false;
        return Tile.damagePerSecond(tiles[idx(tx, ty, width)]) > 0f;
    }

    /** Returns the speed modifier for the tile at the given world position (1.0 = normal). */
    public float tileSpeedModifier(float wx, float wy) {
        int tx = (int)Math.floor(wx);
        int ty = (int)Math.floor(wy);
        if (!inBounds(tx, ty)) return 1f;
        return Tile.speedModifier(tiles[idx(tx, ty, width)]);
    }

    private boolean inBounds(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    private static int idx(int x, int y, int width) {
        return y * width + x;
    }

    public boolean isProjectileBlockedWorld(float wx, float wy) {
        int tx = (int)Math.floor(wx);
        int ty = (int)Math.floor(wy);
        if (!inBounds(tx, ty)) return true; // outside map blocks
        return Tile.blocksProjectile(tiles[idx(tx, ty, width)]);
    }
}
