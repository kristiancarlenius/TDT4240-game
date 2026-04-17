package com.mygame.server.domain.system;

import com.mygame.server.domain.model.PlayerState;
import com.mygame.server.domain.model.ServerGameState;
import com.mygame.shared.dto.TileType;
import com.mygame.shared.util.Vec2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FR2   — Players shall control a character within the game world.
 * FR2.2 — System shall allow the player to control the direction the character is facing.
 */
class CollisionSystemTest {

    private ServerGameState state;
    private CollisionSystem collisionSystem;
    private PlayerState player;

    // 10x10: border walls, interior floor
    @BeforeEach
    void setup() {
        TileType[] tiles = new TileType[10 * 10];
        for (int i = 0; i < tiles.length; i++) tiles[i] = TileType.FLOOR;
        for (int x = 0; x < 10; x++) {
            tiles[x] = TileType.WALL;
            tiles[9 * 10 + x] = TileType.WALL;
        }
        for (int y = 0; y < 10; y++) {
            tiles[y * 10] = TileType.WALL;
            tiles[y * 10 + 9] = TileType.WALL;
        }
        state = ServerGameState.fromTiles("test", 10, 10, tiles);
        collisionSystem = new CollisionSystem(state);
        player = new PlayerState("p1", "TestPlayer", new Vec2(5f, 5f));
    }

    // FR2 — player moves in response to input
    @Test
    void applyMovement_movesPlayerInMoveDirection() {
        Vec2 before = new Vec2(player.pos.x, player.pos.y);
        collisionSystem.applyMovement(player, new Vec2(1f, 0f), new Vec2(1f, 0f), 0.1f);
        assertTrue(player.pos.x > before.x, "Player should move right");
    }

    @Test
    void applyMovement_noMove_playerStaysStill() {
        collisionSystem.applyMovement(player, Vec2.zero(), Vec2.zero(), 0.1f);
        assertEquals(5f, player.pos.x, 0.001f);
        assertEquals(5f, player.pos.y, 0.001f);
    }

    @Test
    void applyMovement_blockedByWall_doesNotEnterWall() {
        player.pos = new Vec2(1.5f, 5f); // one tile from left wall
        collisionSystem.applyMovement(player, new Vec2(-1f, 0f), new Vec2(-1f, 0f), 2f);
        assertTrue(state.isWalkableWorld(player.pos.x, player.pos.y),
                "Player must remain on walkable tile");
    }

    @Test
    void applyMovement_diagonalNormalized_speedNotDoubled() {
        Vec2 before = new Vec2(player.pos.x, player.pos.y);
        collisionSystem.applyMovement(player, new Vec2(1f, 1f), Vec2.zero(), 0.1f);
        float dx = player.pos.x - before.x;
        float dy = player.pos.y - before.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        // With 1f,1f diagonal and dt=0.1, max expected dist is ~0.45 (BASE_SPEED * dt)
        assertTrue(dist < 0.6f, "Diagonal movement should not exceed normal speed");
    }

    // FR2.2 — facing direction updated by aim input
    @Test
    void applyMovement_aimRight_facingUpdatedToRight() {
        collisionSystem.applyMovement(player, Vec2.zero(), new Vec2(1f, 0f), 0.1f);
        assertEquals(1f, player.facing.x, 0.001f);
        assertEquals(0f, player.facing.y, 0.001f);
    }

    @Test
    void applyMovement_aimUp_facingUpdatedToUp() {
        collisionSystem.applyMovement(player, Vec2.zero(), new Vec2(0f, 1f), 0.1f);
        assertEquals(0f, player.facing.x, 0.001f);
        assertEquals(1f, player.facing.y, 0.001f);
    }

    @Test
    void applyMovement_aimDiagonal_facingNormalized() {
        collisionSystem.applyMovement(player, Vec2.zero(), new Vec2(1f, 1f), 0.1f);
        float len = (float) Math.sqrt(player.facing.x * player.facing.x + player.facing.y * player.facing.y);
        assertEquals(1f, len, 0.001f, "Facing vector must be unit length");
    }

    @Test
    void applyMovement_zeroAim_facingUnchanged() {
        player.facing = new Vec2(1f, 0f);
        collisionSystem.applyMovement(player, Vec2.zero(), Vec2.zero(), 0.1f);
        assertEquals(1f, player.facing.x, 0.001f);
    }

    @Test
    void applyMovement_cobwebTile_reducesSpeed() {
        TileType[] tiles = new TileType[5 * 5];
        for (int i = 0; i < tiles.length; i++) tiles[i] = TileType.FLOOR;
        tiles[2 * 5 + 2] = TileType.COBWEB;
        ServerGameState cobwebState = ServerGameState.fromTiles("cobweb", 5, 5, tiles);
        CollisionSystem cobwebCollision = new CollisionSystem(cobwebState);

        PlayerState p = new PlayerState("p1", "P", new Vec2(2.5f, 2.5f));
        float normalX = 5f + PlayerState.BASE_MOVE_SPEED * 0.1f;

        cobwebCollision.applyMovement(p, new Vec2(1f, 0f), new Vec2(1f, 0f), 0.1f);
        assertTrue(p.pos.x < normalX, "Cobweb should slow movement");
    }
}
