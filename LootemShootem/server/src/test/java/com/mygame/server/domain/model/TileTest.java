package com.mygame.server.domain.model;

import com.mygame.shared.dto.TileType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** FR5.2 — Each tile type shall define specific interaction rules. */
class TileTest {

    @Test
    void floor_isWalkable() {
        assertTrue(Tile.isWalkable(TileType.FLOOR));
    }

    @Test
    void wall_isNotWalkable() {
        assertFalse(Tile.isWalkable(TileType.WALL));
    }

    @Test
    void window_isNotWalkable() {
        assertFalse(Tile.isWalkable(TileType.WINDOW));
    }

    @Test
    void trap_isWalkable() {
        assertTrue(Tile.isWalkable(TileType.TRAP));
    }

    @Test
    void cobweb_isWalkable() {
        assertTrue(Tile.isWalkable(TileType.COBWEB));
    }

    @Test
    void wall_blocksProjectile() {
        assertTrue(Tile.blocksProjectile(TileType.WALL));
    }

    @Test
    void window_doesNotBlockProjectile() {
        assertFalse(Tile.blocksProjectile(TileType.WINDOW));
    }

    @Test
    void floor_doesNotBlockProjectile() {
        assertFalse(Tile.blocksProjectile(TileType.FLOOR));
    }

    @Test
    void trap_dealsDamagePerSecond() {
        assertTrue(Tile.damagePerSecond(TileType.TRAP) > 0f);
    }

    @Test
    void floor_dealsNoDamage() {
        assertEquals(0f, Tile.damagePerSecond(TileType.FLOOR));
    }

    @Test
    void cobweb_reducesSpeedToOneThird() {
        assertEquals(1f / 3f, Tile.speedModifier(TileType.COBWEB), 0.0001f);
    }

    @Test
    void floor_hasNormalSpeed() {
        assertEquals(1f, Tile.speedModifier(TileType.FLOOR), 0.0001f);
    }
}
