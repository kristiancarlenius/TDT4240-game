package com.mygame.server.domain.system;

import com.mygame.server.domain.model.PlayerState;
import com.mygame.server.domain.model.ServerGameState;
import com.mygame.shared.dto.TileType;
import com.mygame.shared.util.Vec2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FR10 — Health and damage.
 * FR11 — Player elimination and respawn.
 */
class PlayerSystemTest {

    private ServerGameState state;
    private PlayerSystem playerSystem;
    private PlayerState victim;
    private PlayerState killer;

    @BeforeEach
    void setup() {
        TileType[] tiles = new TileType[10 * 10];
        for (int i = 0; i < tiles.length; i++) tiles[i] = TileType.FLOOR;
        state = ServerGameState.fromTiles("test", 10, 10, tiles);
        playerSystem = new PlayerSystem(state);

        victim = new PlayerState("v1", "Victim", new Vec2(5f, 5f));
        killer = new PlayerState("k1", "Killer", new Vec2(3f, 3f));
        state.players.put(victim.playerId, victim);
        state.players.put(killer.playerId, killer);
    }

    // FR10 — takeDamage reduces HP
    @Test
    void takeDamage_reducesHp() {
        playerSystem.takeDamage(victim, 30f, killer.playerId);
        assertEquals(70f, victim.hp, 0.001f);
    }

    @Test
    void takeDamage_hpNeverGoesNegative() {
        playerSystem.takeDamage(victim, 200f, killer.playerId);
        assertEquals(0f, victim.hp, 0.001f);
    }

    // FR11.1 — Player marked as dead when HP reaches zero
    @Test
    void takeDamage_killsPlayerAtZeroHp() {
        playerSystem.takeDamage(victim, 100f, killer.playerId);
        assertTrue(victim.isDead);
    }

    @Test
    void takeDamage_doesNotKillWithPartialDamage() {
        playerSystem.takeDamage(victim, 50f, killer.playerId);
        assertFalse(victim.isDead);
    }

    // FR11.3 — Killer score increases on kill
    @Test
    void kill_incrementsKillerScore() {
        playerSystem.takeDamage(victim, 100f, killer.playerId);
        assertEquals(1, killer.score);
    }

    @Test
    void kill_incrementsKillerKillsThisLife() {
        playerSystem.takeDamage(victim, 100f, killer.playerId);
        assertEquals(1, killer.killsThisLife);
    }

    // FR11.4 — Kill feed message added
    @Test
    void kill_addsKillFeedMessage() {
        playerSystem.takeDamage(victim, 100f, killer.playerId);
        assertFalse(state.killFeedQueue.isEmpty());
        assertTrue(state.killFeedQueue.get(0).contains(killer.username));
        assertTrue(state.killFeedQueue.get(0).contains(victim.username));
    }

    // Dead player cannot take more damage
    @Test
    void deadPlayer_ignoresDamage() {
        victim.isDead = true;
        victim.hp = 100f;
        playerSystem.takeDamage(victim, 50f, killer.playerId);
        assertEquals(100f, victim.hp, 0.001f);
    }

    // FR11.2 — Respawn resets health and marks player alive
    @Test
    void respawn_resetsHealthAndAliveState() {
        playerSystem.takeDamage(victim, 100f, killer.playerId);
        assertTrue(victim.isDead);

        // Advance time past respawn timer (5 seconds)
        playerSystem.update(6f);

        assertFalse(victim.isDead);
        assertEquals(100f, victim.hp, 0.001f);
    }

    @Test
    void respawn_placesPlayerOnWalkableTile() {
        playerSystem.takeDamage(victim, 100f, killer.playerId);
        playerSystem.update(6f);
        assertTrue(state.isWalkableWorld(victim.pos.x, victim.pos.y));
    }

    // Hurt timer is set on damage
    @Test
    void takeDamage_setsHurtTimer() {
        playerSystem.takeDamage(victim, 10f, killer.playerId);
        assertTrue(victim.hurtTimer > 0f);
    }

    // Hurt timer decreases over time
    @Test
    void update_decaysHurtTimer() {
        victim.hurtTimer = 1f;
        playerSystem.update(0.5f);
        assertEquals(0.5f, victim.hurtTimer, 0.001f);
    }
}
