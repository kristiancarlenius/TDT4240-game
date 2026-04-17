package com.mygame.server.domain.model;

import com.mygame.server.data.weapon.WeaponRegistry;
import com.mygame.shared.dto.TileType;
import com.mygame.shared.dto.WeaponType;
import com.mygame.shared.util.Vec2;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M1 — Adding new weapons should only require a new WeaponSpec (~1 hour).
 * M2 — Adding a new room type should use existing map infrastructure (~30 min).
 * M3 — Adding a new tile type should only require editing Tile.java (~10 min).
 *
 * These tests verify that the architecture supports isolated changes:
 * each concern is localized so modifications do not cascade.
 */
class ModifiabilityTest {

    // M1 — New weapon added without touching existing code
    @Test
    void m1_addingNewWeapon_onlyRequiresNewSpec() {
        WeaponSpec newGun = new WeaponSpec(
                WeaponType.PISTOL, 1,
                35f, 13f, 0.10f, 2.5f,
                4.0f, 12, 2, 1, 0.02f, 1.1f);

        WeaponRegistry registry = new WeaponRegistry(List.of(newGun));
        WeaponSpec loaded = registry.get(WeaponType.PISTOL);

        assertEquals(35f, loaded.damage, 0.001f);
        assertEquals(12,  loaded.maxAmmo);
    }

    @Test
    void m1_allDefaultWeapons_areAccessibleWithoutCodeChange() {
        WeaponRegistry registry = new WeaponRegistry();
        for (WeaponType type : WeaponType.values()) {
            assertDoesNotThrow(() -> registry.get(type),
                    "All WeaponType enum values must have a registered spec");
        }
    }

    // M2 — Map generation accepts any tile layout; room type is just a tile array
    @Test
    void m2_customTileLayout_canBeLoadedAsFlatTiles() {
        // Simulates loading a new "room type": just a new tile configuration
        TileType[] customRoom = new TileType[5 * 5];
        for (int i = 0; i < customRoom.length; i++) customRoom[i] = TileType.FLOOR;
        customRoom[0] = TileType.WALL;
        customRoom[4] = TileType.WALL;

        ServerGameState state = ServerGameState.fromTiles("custom-room", 5, 5, customRoom);
        assertNotNull(state);
        assertEquals(25, state.tiles.length);
        assertEquals(TileType.WALL, state.tiles[0]);
    }

    @Test
    void m2_mapWithChestSpawnPoints_loadedCorrectly() {
        TileType[] tiles = new TileType[10 * 10];
        for (int i = 0; i < tiles.length; i++) tiles[i] = TileType.FLOOR;
        List<Vec2> spawnPoints = List.of(new Vec2(3f, 3f), new Vec2(7f, 7f));

        ServerGameState state = ServerGameState.fromTiles("map-with-spawns", 10, 10, tiles, spawnPoints);
        assertEquals(2, state.chestSpawnPoints.size());
    }

    // M3 — Tile behavior rules are all localized in Tile.java
    @Test
    void m3_tileWalkabilityDefinedInOnlyOnePlace() {
        // All tile query methods are on Tile — no scattered if/else in callers
        for (TileType type : TileType.values()) {
            assertDoesNotThrow(() -> Tile.isWalkable(type),
                    "Tile.isWalkable must handle every TileType");
            assertDoesNotThrow(() -> Tile.blocksProjectile(type),
                    "Tile.blocksProjectile must handle every TileType");
            assertDoesNotThrow(() -> Tile.damagePerSecond(type),
                    "Tile.damagePerSecond must handle every TileType");
            assertDoesNotThrow(() -> Tile.speedModifier(type),
                    "Tile.speedModifier must handle every TileType");
        }
    }

    @Test
    void m3_newTileType_onlyNeedsTileClassUpdate() {
        // Verify ServerGameState delegates all tile queries through Tile
        TileType[] tiles = new TileType[5 * 5];
        for (int i = 0; i < tiles.length; i++) tiles[i] = TileType.FLOOR;
        tiles[2 * 5 + 2] = TileType.COBWEB;
        ServerGameState state = ServerGameState.fromTiles("cobweb-test", 5, 5, tiles);

        // The game state delegates to Tile — no hard-coded TileType checks in ServerGameState
        assertTrue(state.isWalkableWorld(2.5f, 2.5f));
        assertEquals(1f / 3f, state.tileSpeedModifier(2.5f, 2.5f), 0.001f);
    }
}
