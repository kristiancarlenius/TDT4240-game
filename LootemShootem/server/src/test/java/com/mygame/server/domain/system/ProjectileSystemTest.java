package com.mygame.server.domain.system;

import com.mygame.server.domain.model.PlayerState;
import com.mygame.server.domain.model.ProjectileState;
import com.mygame.server.domain.model.ServerGameState;
import com.mygame.shared.dto.TileType;
import com.mygame.shared.util.Vec2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** FR8.2 — Weapons shall fire projectiles with defined speed and interaction behavior. */
class ProjectileSystemTest {

    private ServerGameState state;
    private PlayerSystem playerSystem;
    private ProjectileSystem projectileSystem;

    // 10x10 map: all floor except border walls
    @BeforeEach
    void setup() {
        TileType[] tiles = new TileType[10 * 10];
        for (int i = 0; i < tiles.length; i++) tiles[i] = TileType.FLOOR;
        // border walls
        for (int x = 0; x < 10; x++) {
            tiles[x] = TileType.WALL;
            tiles[9 * 10 + x] = TileType.WALL;
        }
        for (int y = 0; y < 10; y++) {
            tiles[y * 10] = TileType.WALL;
            tiles[y * 10 + 9] = TileType.WALL;
        }

        state = ServerGameState.fromTiles("test", 10, 10, tiles);
        playerSystem = new PlayerSystem(state);
        projectileSystem = new ProjectileSystem(state, playerSystem);
    }

    private ProjectileState makeProjectile(String id, String ownerId, Vec2 pos, Vec2 vel, float damage) {
        return new ProjectileState(id, ownerId, pos, vel, damage, 0.1f, 10f);
    }

    @Test
    void projectile_movesAlongVelocity() {
        PlayerState owner = new PlayerState("o1", "Owner", new Vec2(5f, 5f));
        state.players.put(owner.playerId, owner);

        ProjectileState pr = makeProjectile("p1", "o1", new Vec2(3f, 3f), new Vec2(2f, 0f), 10f);
        state.projectiles.add(pr);

        projectileSystem.update(0.5f);

        assertEquals(4f, pr.pos.x, 0.05f);
    }

    @Test
    void projectile_removedWhenHittingWall() {
        PlayerState owner = new PlayerState("o1", "Owner", new Vec2(5f, 5f));
        state.players.put(owner.playerId, owner);

        // Projectile heading directly into the left wall
        ProjectileState pr = makeProjectile("p1", "o1", new Vec2(1.5f, 5f), new Vec2(-20f, 0f), 10f);
        state.projectiles.add(pr);

        projectileSystem.update(0.2f);

        assertTrue(state.projectiles.isEmpty(), "Projectile should be removed after hitting wall");
    }

    @Test
    void projectile_removedWhenExpired() {
        PlayerState owner = new PlayerState("o1", "Owner", new Vec2(5f, 5f));
        state.players.put(owner.playerId, owner);

        ProjectileState pr = makeProjectile("p1", "o1", new Vec2(5f, 5f), new Vec2(0f, 0f), 10f);
        pr.ttlSeconds = 0.1f;
        state.projectiles.add(pr);

        projectileSystem.update(1f);

        assertTrue(state.projectiles.isEmpty(), "Projectile should be removed when TTL expires");
    }

    @Test
    void projectile_dealsDamageToPlayer() {
        PlayerState owner = new PlayerState("o1", "Owner", new Vec2(1f, 1f));
        PlayerState target = new PlayerState("t1", "Target", new Vec2(6f, 5f));
        state.players.put(owner.playerId, owner);
        state.players.put(target.playerId, target);

        // Place projectile right on top of target
        ProjectileState pr = makeProjectile("p1", "o1", new Vec2(6f, 5f), new Vec2(0f, 0f), 40f);
        state.projectiles.add(pr);

        projectileSystem.update(0.016f);

        assertEquals(60f, target.hp, 0.001f);
    }

    @Test
    void projectile_doesNotDamageOwner() {
        PlayerState owner = new PlayerState("o1", "Owner", new Vec2(5f, 5f));
        state.players.put(owner.playerId, owner);

        // Projectile at same position as owner
        ProjectileState pr = makeProjectile("p1", "o1", new Vec2(5f, 5f), new Vec2(0f, 0f), 50f);
        state.projectiles.add(pr);

        projectileSystem.update(0.016f);

        assertEquals(100f, owner.hp, 0.001f);
    }

    @Test
    void projectile_doesNotDamageDeadPlayer() {
        PlayerState owner = new PlayerState("o1", "Owner", new Vec2(1f, 1f));
        PlayerState target = new PlayerState("t1", "Target", new Vec2(6f, 5f));
        target.isDead = true;
        state.players.put(owner.playerId, owner);
        state.players.put(target.playerId, target);

        ProjectileState pr = makeProjectile("p1", "o1", new Vec2(6f, 5f), new Vec2(0f, 0f), 50f);
        state.projectiles.add(pr);

        projectileSystem.update(0.016f);

        assertEquals(100f, target.hp, 0.001f);
    }

    @Test
    void projectile_removedAfterHittingPlayer() {
        PlayerState owner = new PlayerState("o1", "Owner", new Vec2(1f, 1f));
        PlayerState target = new PlayerState("t1", "Target", new Vec2(6f, 5f));
        state.players.put(owner.playerId, owner);
        state.players.put(target.playerId, target);

        ProjectileState pr = makeProjectile("p1", "o1", new Vec2(6f, 5f), new Vec2(0f, 0f), 10f);
        state.projectiles.add(pr);

        projectileSystem.update(0.016f);

        assertTrue(state.projectiles.isEmpty(), "Projectile should be removed on player hit");
    }
}
