package com.mygame.server.domain.model;

import com.mygame.shared.dto.TileType;
import com.mygame.shared.util.Vec2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** FR5 — Map world queries: walkability, collision, trap detection. */
class ServerGameStateTest {

    private ServerGameState state;

    // 5x5 map: border walls, center is floor, tile (2,2) is TRAP
    @BeforeEach
    void setup() {
        TileType[] tiles = new TileType[5 * 5];
        for (int i = 0; i < tiles.length; i++) tiles[i] = TileType.WALL;
        // Interior floor
        for (int y = 1; y <= 3; y++)
            for (int x = 1; x <= 3; x++)
                tiles[y * 5 + x] = TileType.FLOOR;
        // One trap tile
        tiles[2 * 5 + 2] = TileType.TRAP;

        state = ServerGameState.fromTiles("test", 5, 5, tiles);
    }

    @Test
    void floorTile_isWalkable() {
        assertTrue(state.isWalkableWorld(1.5f, 1.5f));
    }

    @Test
    void wallTile_isNotWalkable() {
        assertFalse(state.isWalkableWorld(0.5f, 0.5f));
    }

    @Test
    void trapTile_isWalkable() {
        assertTrue(state.isWalkableWorld(2.5f, 2.5f));
    }

    @Test
    void trapTile_detectedAsTrap() {
        assertTrue(state.isTrapAtWorld(2.5f, 2.5f));
    }

    @Test
    void floorTile_isNotTrap() {
        assertFalse(state.isTrapAtWorld(1.5f, 1.5f));
    }

    @Test
    void outOfBounds_isNotWalkable() {
        assertFalse(state.isWalkableWorld(-1f, -1f));
        assertFalse(state.isWalkableWorld(10f, 10f));
    }

    @Test
    void wallTile_blocksProjectile() {
        assertTrue(state.isProjectileBlockedWorld(0.5f, 0.5f));
    }

    @Test
    void floorTile_doesNotBlockProjectile() {
        assertFalse(state.isProjectileBlockedWorld(1.5f, 1.5f));
    }

    @Test
    void outOfBounds_blocksProjectile() {
        assertTrue(state.isProjectileBlockedWorld(-1f, 0f));
    }

    @Test
    void collidePlayer_allowsMovementIntoFloor() {
        Vec2 result = state.collidePlayer(new Vec2(1.5f, 1.5f), new Vec2(1.8f, 1.5f));
        assertEquals(1.8f, result.x, 0.001f);
    }

    @Test
    void collidePlayer_preventsMovementIntoWall() {
        Vec2 old = new Vec2(1.5f, 1.5f);
        Vec2 intoWall = new Vec2(0.5f, 0.5f);
        Vec2 result = state.collidePlayer(old, intoWall);
        // Should not reach the wall tile
        assertTrue(state.isWalkableWorld(result.x, result.y));
    }

    @Test
    void findNextSpawn_returnsWalkablePosition() {
        Vec2 spawn = state.findNextSpawn();
        assertTrue(state.isWalkableWorld(spawn.x, spawn.y),
                "Spawn position must be walkable");
    }

    @Test
    void cobweb_reducesSpeedModifier() {
        TileType[] tiles = new TileType[3 * 3];
        for (int i = 0; i < tiles.length; i++) tiles[i] = TileType.FLOOR;
        tiles[1 * 3 + 1] = TileType.COBWEB;
        ServerGameState s = ServerGameState.fromTiles("cobweb", 3, 3, tiles);
        assertEquals(1f / 3f, s.tileSpeedModifier(1.5f, 1.5f), 0.0001f);
    }
}
