package com.mygame.server.domain.system;

import com.mygame.server.data.weapon.WeaponRegistry;
import com.mygame.server.domain.model.PickupState;
import com.mygame.server.domain.model.PlayerState;
import com.mygame.server.domain.model.ProjectileState;
import com.mygame.server.domain.model.ServerGameState;
import com.mygame.shared.dto.PickupType;
import com.mygame.shared.dto.WeaponType;
import com.mygame.shared.protocol.messages.InputMessage;
import com.mygame.shared.util.Vec2;

import java.util.Map;
import java.util.UUID;

public final class ServerWorldStepSystem {

    private static final float HP_REGEN_PER_SEC = 2f;

    private final ServerGameState   state;
    private final WeaponRegistry    weaponRegistry;
    private final CollisionSystem   collisionSystem;
    private final ProjectileSystem  projectileSystem;
    private final PickupSpawnSystem pickupSpawnSystem;
    private final PlayerSystem      playerSystem;

    public ServerWorldStepSystem(ServerGameState state,
                                 WeaponRegistry weaponRegistry,
                                 CollisionSystem collisionSystem,
                                 ProjectileSystem projectileSystem,
                                 PickupSpawnSystem pickupSpawnSystem,
                                 PlayerSystem playerSystem) {
        this.state             = state;
        this.weaponRegistry    = weaponRegistry;
        this.collisionSystem   = collisionSystem;
        this.projectileSystem  = projectileSystem;
        this.pickupSpawnSystem = pickupSpawnSystem;
        this.playerSystem      = playerSystem;
    }

    public void tick(float dt, Map<String, InputMessage> latestInput) {
        state.tick++;

        pickupSpawnSystem.update(dt);
        playerSystem.update(dt);

        for (PlayerState p : state.players.values()) {
            if (p.isDead) {
                // FR11.5: drop loot on the first tick after death
                if (p.justDied) {
                    p.justDied = false;
                    dropLoot(p);
                }
                continue;
            }

            p.timeSurvived += dt;
            p.hp = Math.min(100f, p.hp + HP_REGEN_PER_SEC * dt);

            collisionSystem.tickSpeedBoost(p, dt);

            InputMessage in = latestInput.get(p.playerId);
            Vec2 move = (in != null && in.move != null) ? in.move : Vec2.zero();
            Vec2 aim  = (in != null && in.aim  != null) ? in.aim  : Vec2.zero();

            collisionSystem.applyMovement(p, move, aim, dt);

            // Shoot cooldown
            p.shootCooldownSeconds = Math.max(0f, p.shootCooldownSeconds - dt);

            if (in != null && in.switchWeapon && in.seq > p.lastSwitchSeq) {
                p.lastSwitchSeq = in.seq;
                int nextSlot    = 1 - p.currentSlot;
                if (p.inventory[nextSlot] != null) {
                    p.ammoBySlot[p.currentSlot] = p.equippedAmmo; // save current ammo
                    p.currentSlot               = nextSlot;
                    p.syncEquipped();
                    p.shootCooldownSeconds = 0f;
                }
            }

            // Shooting
            if (in != null && in.shoot
                    && p.shootCooldownSeconds <= 0f
                    && p.equippedAmmo > 0) {
                var spec = weaponRegistry.get(p.equippedWeaponType);
                spawnProjectiles(p, spec);
                p.equippedAmmo--;
                p.ammoBySlot[p.currentSlot] = p.equippedAmmo; // keep slot in sync
                p.shootCooldownSeconds = 1f / spec.fireRate;
            }

            // Trap damage
            if (state.isTrapAtWorld(p.pos.x, p.pos.y)) {
                playerSystem.takeDamage(p, 10f * dt, null);
            }
        }

        projectileSystem.update(dt);
    }

    private void dropLoot(PlayerState p) {
        int secSlot = 1 - p.currentSlot;
        if (p.inventory[secSlot] != null) {
            state.pickups.add(new PickupState(
                    UUID.randomUUID().toString(),
                    PickupType.WEAPON,
                    new Vec2(p.pos.x, p.pos.y),
                    0, 0f,
                    p.inventory[secSlot],
                    Math.max(1, p.ammoBySlot[secSlot])));
        } else {
            state.pickups.add(new PickupState(
                    UUID.randomUUID().toString(),
                    PickupType.HEALTH,
                    new Vec2(p.pos.x, p.pos.y),
                    25, 0f, null, 0));
        }
    }

    private void spawnProjectiles(PlayerState p, com.mygame.server.domain.model.WeaponSpec spec) {
        float sx      = p.pos.x + p.facing.x * (p.radius + 0.15f);
        float sy      = p.pos.y + p.facing.y * (p.radius + 0.15f);
        int   pellets = Math.max(1, spec.pellets);

        for (int k = 0; k < pellets; k++) {
            float fx = p.facing.x;
            float fy = p.facing.y;

            if (spec.spreadRadians > 0f) {
                float angle  = (float) Math.atan2(fy, fx);
                float jitter = (float) (Math.random() * 2 - 1) * spec.spreadRadians;
                fx = (float) Math.cos(angle + jitter);
                fy = (float) Math.sin(angle + jitter);
            }

            state.projectiles.add(new ProjectileState(
                    UUID.randomUUID().toString(),
                    p.playerId,
                    new Vec2(sx, sy),
                    new Vec2(fx * spec.projectileSpeed, fy * spec.projectileSpeed),
                    spec.damage,
                    spec.projectileRadius,
                    spec.ttlSeconds));
        }
    }
}
