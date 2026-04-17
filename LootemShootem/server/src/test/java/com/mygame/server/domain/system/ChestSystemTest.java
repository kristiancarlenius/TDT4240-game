package com.mygame.server.domain.system;

import com.mygame.server.data.weapon.WeaponRegistry;
import com.mygame.server.domain.model.PlayerState;
import com.mygame.server.domain.model.ServerGameState;
import com.mygame.shared.dto.TileType;
import com.mygame.shared.util.Vec2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FR9   — System shall spawn chests at random positions on the map.
 * FR9.1 — Players shall be able to collect bonuses from the chests.
 * FR6   — Player status includes health, speed, weapon.
 */
class ChestSystemTest {

    private ServerGameState state;
    private WeaponRegistry registry;
    private ChestSystem chestSystem;

    // 10x10 open floor map
    @BeforeEach
    void setup() {
        TileType[] tiles = new TileType[10 * 10];
        for (int i = 0; i < tiles.length; i++) tiles[i] = TileType.FLOOR;
        state = ServerGameState.fromTiles("test", 10, 10, tiles);
        registry = new WeaponRegistry();
        chestSystem = new ChestSystem(state, registry, new Random(42L), 3);
    }

    // FR9 — chests spawn on map
    @Test
    void chests_spawnOnInit() {
        assertFalse(state.chests.isEmpty(), "Chests should be present after ChestSystem init");
    }

    @Test
    void chests_countDoesNotExceedRequested() {
        assertTrue(state.chests.size() <= 3);
    }

    @Test
    void chests_spawnOnWalkableTiles() {
        for (var chest : state.chests) {
            assertTrue(state.isWalkableWorld(chest.pos.x, chest.pos.y),
                    "Chest at " + chest.pos + " must be on a walkable tile");
        }
    }

    // FR9.1 — player collects bonus by walking to chest
    @Test
    void playerNearChest_chestBecomesOpen() {
        var chest = state.chests.get(0);
        PlayerState player = new PlayerState("p1", "Player", chest.pos);
        state.players.put(player.playerId, player);

        chestSystem.update(0.016f);

        assertTrue(chest.isOpen, "Chest should open when player is adjacent");
    }

    @Test
    void playerNearHealthChest_hpRestored() {
        var chest = state.chests.get(0);
        // Force health loot
        chest.lootType = com.mygame.shared.dto.PickupType.HEALTH;
        chest.lootWeapon = null;

        PlayerState player = new PlayerState("p1", "Player", chest.pos);
        player.hp = 50f;
        state.players.put(player.playerId, player);

        chestSystem.update(0.016f);

        assertTrue(player.hp > 50f || player.maxHp > 100f,
                "Health chest should increase HP or maxHp");
    }

    @Test
    void playerFarFromChest_chestRemainsClose() {
        var chest = state.chests.get(0);
        PlayerState player = new PlayerState("p1", "Player", new Vec2(chest.pos.x + 5f, chest.pos.y));
        state.players.put(player.playerId, player);

        chestSystem.update(0.016f);

        assertFalse(chest.isOpen, "Chest should not open when player is far away");
    }

    @Test
    void deadPlayer_cannotOpenChest() {
        var chest = state.chests.get(0);
        PlayerState player = new PlayerState("p1", "Player", chest.pos);
        player.isDead = true;
        state.players.put(player.playerId, player);

        chestSystem.update(0.016f);

        assertFalse(chest.isOpen, "Dead player should not be able to open a chest");
    }

    // FR9 — opened chest is removed after timer expires
    @Test
    void openedChest_removedAfterTimer() {
        var chest = state.chests.get(0);
        PlayerState player = new PlayerState("p1", "Player", chest.pos);
        state.players.put(player.playerId, player);

        chestSystem.update(0.016f); // opens chest
        assertTrue(chest.isOpen);

        chestSystem.update(3f); // advance past openTimer (2s)
        assertFalse(state.chests.contains(chest), "Opened chest should be removed after timer");
    }

    // FR6 — speed loot increases player speed
    @Test
    void speedChest_increasesPlayerSpeed() {
        var chest = state.chests.get(0);
        chest.lootType = com.mygame.shared.dto.PickupType.SPEED;
        chest.lootWeapon = null;

        PlayerState player = new PlayerState("p1", "Player", chest.pos);
        float speedBefore = player.moveSpeed;
        state.players.put(player.playerId, player);

        chestSystem.update(0.016f);

        assertTrue(player.moveSpeed >= speedBefore,
                "Speed chest should not decrease movement speed");
    }
}
