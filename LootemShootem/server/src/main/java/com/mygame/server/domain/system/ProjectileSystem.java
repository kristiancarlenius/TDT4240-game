package com.mygame.server.domain.system;

import com.mygame.server.domain.model.PlayerState;
import com.mygame.server.domain.model.ProjectileState;
import com.mygame.server.domain.model.ServerGameState;
import com.mygame.shared.util.Vec2;

/**
 * Moves all projectiles, removes expired / wall-blocked ones,
 * and notifies PlayerSystem on player hit.
 */
public final class ProjectileSystem {

    private final ServerGameState state;
    private final PlayerSystem playerSystem;

    public ProjectileSystem(ServerGameState state, PlayerSystem playerSystem) {
        this.state = state;
        this.playerSystem = playerSystem;
    }

    public void update(float dt) {
        for (int i = state.projectiles.size() - 1; i >= 0; i--) {
            ProjectileState pr = state.projectiles.get(i);

            pr.ttlSeconds -= dt;
            if (pr.ttlSeconds <= 0f) {
                state.projectiles.remove(i);
                continue;
            }

            Vec2 newPos = new Vec2(pr.pos.x + pr.vel.x * dt, pr.pos.y + pr.vel.y * dt);
            if (state.isProjectileBlockedWorld(newPos.x, newPos.y)) {
                state.projectiles.remove(i);
                continue;
            }
            pr.pos = newPos;

            PlayerState hit = findHit(pr);
            if (hit != null) {
                playerSystem.takeDamage(hit, pr.damage, pr.ownerPlayerId);
                state.projectiles.remove(i);
            }
        }
    }

    private PlayerState findHit(ProjectileState pr) {
        for (PlayerState p : state.players.values()) {
            if (p.playerId.equals(pr.ownerPlayerId)) continue;
            if (p.isDead) continue;
            float dx = p.pos.x - pr.pos.x;
            float dy = p.pos.y - pr.pos.y;
            float r  = p.radius + pr.radius;
            if (dx * dx + dy * dy <= r * r) return p;
        }
        return null;
    }
}
