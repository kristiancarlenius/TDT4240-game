package com.mygame.server.domain.system;

import com.mygame.server.domain.model.PlayerState;
import com.mygame.server.domain.model.ServerGameState;
import com.mygame.shared.util.Vec2;

/**
 * Handles player movement, tile collision, facing updates, and tile-based
 * speed modifiers (cobweb, etc.).
 */
public final class CollisionSystem {

    private final ServerGameState state;

    public CollisionSystem(ServerGameState state) {
        this.state = state;
    }

    /**
     * Integrates the player's position for one tick, applying:
     *  - Diagonal normalisation
     *  - Tile cobweb speed modifier
     *  - Axis-separated tile collision
     *  - Facing update from aim vector
     */
    public void applyMovement(PlayerState p, Vec2 move, Vec2 aim, float dt) {
        float mx = clamp(move.x, -1f, 1f);
        float my = clamp(move.y, -1f, 1f);
        float len2 = mx * mx + my * my;
        if (len2 > 1f) {
            float inv = (float)(1.0 / Math.sqrt(len2));
            mx *= inv;
            my *= inv;
        }

        float ax = clamp(aim.x, -1f, 1f);
        float ay = clamp(aim.y, -1f, 1f);
        float alen2 = ax * ax + ay * ay;
        if (alen2 > 0.0001f) {
            float inv = (float)(1.0 / Math.sqrt(alen2));
            p.facing = new Vec2(ax * inv, ay * inv);
        }

        // Apply cobweb or any other tile-based speed modifier at current position
        float tileMod = state.tileSpeedModifier(p.pos.x, p.pos.y);
        float speed   = p.moveSpeed * tileMod;

        float vx = mx * speed;
        float vy = my * speed;
        Vec2 newPos = new Vec2(p.pos.x + vx * dt, p.pos.y + vy * dt);
        p.pos = state.collidePlayer(p.pos, newPos);
        p.vel = new Vec2(vx, vy);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
