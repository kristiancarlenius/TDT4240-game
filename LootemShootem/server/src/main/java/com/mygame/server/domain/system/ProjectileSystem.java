package com.mygame.server.domain.system;

import com.mygame.server.domain.model.PlayerState;
import com.mygame.server.domain.model.ProjectileState;
import com.mygame.server.domain.model.ServerGameState;
import com.mygame.shared.util.Vec2;

/**
 * Moves all projectiles, removes expired / wall-blocked ones,
 * and notifies PlayerSystem on player hit.
 * Uses sub-stepping for collision to prevent "tunneling" through enemies at high speeds.
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

            // Sub-stepping checks multiple points along the path.
            int steps = (int) Math.ceil(Math.sqrt(pr.vel.x * pr.vel.x + pr.vel.y * pr.vel.y) * dt / 0.2f);
            steps = Math.max(1, steps);
            
            float stepDt = dt / steps;
            boolean hitFound = false;

            for (int s = 0; s < steps; s++) {
                Vec2 nextSubPos = new Vec2(pr.pos.x + pr.vel.x * stepDt, pr.pos.y + pr.vel.y * stepDt);
                
                if (state.isProjectileBlockedWorld(nextSubPos.x, nextSubPos.y)) {
                    state.projectiles.remove(i);
                    hitFound = true;
                    break;
                }
                
                pr.pos = nextSubPos;
                PlayerState hit = findHit(pr);
                if (hit != null) {
                    playerSystem.takeDamage(hit, pr.damage, pr.ownerPlayerId);
                    state.projectiles.remove(i);
                    hitFound = true;
                    break;
                }
            }

            if (hitFound) continue;
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
