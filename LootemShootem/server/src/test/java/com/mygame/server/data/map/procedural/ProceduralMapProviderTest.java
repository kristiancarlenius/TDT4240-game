package com.mygame.server.data.map.procedural;

import com.mygame.server.domain.model.ServerGameState;
import com.mygame.shared.dto.TileType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P2 — Server shall generate a map in under 3 seconds.
 * FR5  — Game world shall consist of different tile types.
 */
class ProceduralMapProviderTest {

    @Test
    void mapGeneration_completesUnderThreeSeconds() {
        long start = System.currentTimeMillis();
        ProceduralMapProvider provider = new ProceduralMapProvider(42L);
        ServerGameState state = provider.provide("map-perf-test");
        long elapsed = System.currentTimeMillis() - start;

        assertNotNull(state);
        assertTrue(elapsed < 3000,
                "Map generation took " + elapsed + " ms, expected < 3000 ms");
    }

    @Test
    void generatedMap_hasExpectedDimensions() {
        ProceduralMapProvider provider = new ProceduralMapProvider(1L);
        ServerGameState state = provider.provide("map-dim-test");
        assertTrue(state.width > 0);
        assertTrue(state.height > 0);
        assertEquals(state.width * state.height, state.tiles.length);
    }

    @Test
    void generatedMap_containsFloorTiles() {
        ProceduralMapProvider provider = new ProceduralMapProvider(2L);
        ServerGameState state = provider.provide("map-floor-test");

        boolean hasFloor = false;
        for (TileType t : state.tiles) {
            if (t == TileType.FLOOR) { hasFloor = true; break; }
        }
        assertTrue(hasFloor, "Generated map must contain at least one FLOOR tile");
    }

    @Test
    void generatedMap_containsWallTiles() {
        ProceduralMapProvider provider = new ProceduralMapProvider(3L);
        ServerGameState state = provider.provide("map-wall-test");

        boolean hasWall = false;
        for (TileType t : state.tiles) {
            if (t == TileType.WALL) { hasWall = true; break; }
        }
        assertTrue(hasWall, "Generated map must contain at least one WALL tile");
    }

    @Test
    void generatedMap_isSameForSameSeed() {
        ProceduralMapProvider p1 = new ProceduralMapProvider(99L);
        ProceduralMapProvider p2 = new ProceduralMapProvider(99L);
        ServerGameState s1 = p1.provide("same-seed");
        ServerGameState s2 = p2.provide("same-seed");

        assertArrayEquals(s1.tiles, s2.tiles, "Same seed must produce identical maps");
    }

    @Test
    void generatedMap_hasAtLeastOneWalkableSpawnPoint() {
        ProceduralMapProvider provider = new ProceduralMapProvider(7L);
        ServerGameState state = provider.provide("map-spawn-test");
        Vec2Check spawn = new Vec2Check(state.findNextSpawn());
        assertTrue(state.isWalkableWorld(spawn.x, spawn.y),
                "First spawn point must be on a walkable tile");
    }

    private static class Vec2Check {
        final float x, y;
        Vec2Check(com.mygame.shared.util.Vec2 v) { this.x = v.x; this.y = v.y; }
    }
}
