package com.mygame.server.domain.system;

import com.mygame.server.domain.model.PlayerState;
import com.mygame.server.domain.model.ServerGameState;

public final class PlayerSystem {

    private static final float RESPAWN_SECONDS = 5f;
    private static final float HURT_DURATION = 0.15f;

    private final ServerGameState state;

    public PlayerSystem(ServerGameState state) {
        this.state = state;
    }

    public void update(float dt) {
        for (PlayerState p : state.players.values()) {
            if (p.isDead) {
                p.respawnTimer -= dt;
                if (p.respawnTimer <= 0f) {
                    respawnPlayer(p);
                }
            }
            if (p.hurtTimer > 0f) p.hurtTimer -= dt;
            if (p.healTimer > 0f) p.healTimer -= dt;
        }
    }

    public void takeDamage(PlayerState victim, float amount, String attackerId) {
        if (victim.isDead) return;

        victim.hp = Math.max(0f, victim.hp - amount);
        victim.hurtTimer = HURT_DURATION;

        if (victim.hp <= 0f) {
            handleDeath(victim, attackerId);
        }
    }

    private void handleDeath(PlayerState victim, String attackerId) {
        victim.isDead = true;
        victim.justDied = true;
        victim.respawnTimer = RESPAWN_SECONDS;

        PlayerState killer = state.players.get(attackerId);
        if (killer != null) {
            killer.score++;
            killer.killsThisLife++;
            String msg = killer.username + " killed " + victim.username;
            state.killFeedQueue.add(msg);
            System.out.println("[GAME] " + msg);
        }
    }

    private void respawnPlayer(PlayerState p) {
        p.isDead = false;
        p.hp = 100f;
        p.pos = state.findNextSpawn();
        p.speedBoostTimer = 0f;
        p.moveSpeed = PlayerState.BASE_MOVE_SPEED;
        p.hurtTimer = 0f;
        p.killsThisLife = 0;
    }
}
