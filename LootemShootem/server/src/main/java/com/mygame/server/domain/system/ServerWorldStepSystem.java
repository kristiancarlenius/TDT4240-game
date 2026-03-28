package com.mygame.server.domain.system;

import com.mygame.server.data.weapon.WeaponRegistry;
import com.mygame.server.domain.model.*;
import com.mygame.shared.dto.PickupType;
import com.mygame.shared.dto.WeaponType;
import com.mygame.shared.protocol.messages.InputMessage;
import com.mygame.shared.util.Vec2;

import java.util.Map;
import java.util.UUID;

public final class ServerWorldStepSystem {

    private static final float RESPAWN_SECONDS  = 5f;
    /** 0.5 HP/s = 30 HP/min — slow enough that chests matter. */
    private static final float HP_REGEN_PER_SEC = 0.5f;

    private final ServerGameState   state;
    private final WeaponRegistry    weaponRegistry;
    private final CollisionSystem   collisionSystem;
    private final ProjectileSystem  projectileSystem;
    private final PickupSpawnSystem pickupSpawnSystem;
    private final ChestSystem       chestSystem;
    private final PlayerSystem      playerSystem;

    public ServerWorldStepSystem(ServerGameState state,
                                 WeaponRegistry weaponRegistry,
                                 CollisionSystem collisionSystem,
                                 ProjectileSystem projectileSystem,
                                 PickupSpawnSystem pickupSpawnSystem,
                                 PlayerSystem playerSystem,
                                 ChestSystem chestSystem) {
        this.state             = state;
        this.weaponRegistry    = weaponRegistry;
        this.collisionSystem   = collisionSystem;
        this.projectileSystem  = projectileSystem;
        this.pickupSpawnSystem = pickupSpawnSystem;
        this.chestSystem       = chestSystem;
        this.playerSystem      = playerSystem;
    }

    public void tick(float dt, Map<String, InputMessage> latestInput) {
        state.tick++;

        for (PlayerState p : state.players.values()) p.lastPickupNotice = null;

        chestSystem.update(dt);
        pickupSpawnSystem.update();
        playerSystem.update(dt);

        for (PlayerState p : state.players.values()) {
            if (p.isDead) {
                if (p.justDied) { p.justDied = false; dropLoot(p); }
                continue;
            }

            p.timeSurvived += dt;
            p.hp = Math.min(p.maxHp, p.hp + HP_REGEN_PER_SEC * dt);

            InputMessage in   = latestInput.get(p.playerId);
            Vec2         aim  = (in != null && in.aim  != null) ? in.aim  : Vec2.zero();

            // Freeze movement briefly after opening a chest
            if (p.chestFreezeTimer > 0f) {
                p.chestFreezeTimer -= dt;
                collisionSystem.applyMovement(p, Vec2.zero(), aim, dt);
            } else {
                Vec2 move = (in != null && in.move != null) ? in.move : Vec2.zero();
                collisionSystem.applyMovement(p, move, aim, dt);
            }

            tickReload(p, in, dt);

            p.shootCooldownSeconds = Math.max(0f, p.shootCooldownSeconds - dt);

            // Weapon switch — always allowed; cancels any active reload
            if (in != null && in.switchWeapon && in.seq > p.lastSwitchSeq) {
                p.lastSwitchSeq = in.seq;
                int nextSlot = 1 - p.currentSlot;
                if (p.inventory[nextSlot] != null) {
                    p.ammoBySlot[p.currentSlot] = p.equippedAmmo;
                    p.magsBySlot[p.currentSlot] = p.equippedMags;
                    p.currentSlot               = nextSlot;
                    p.syncEquipped();
                    p.shootCooldownSeconds = 0f;
                    p.isReloading          = false;
                    p.reloadTimer          = 0f;
                }
            }

            // Shooting — blocked while reloading
            if (!p.isReloading
                    && in != null && in.shoot
                    && p.shootCooldownSeconds <= 0f
                    && p.equippedAmmo > 0) {
                WeaponSpec spec = weaponRegistry.get(p.equippedWeaponType);
                spawnProjectiles(p, spec);
                p.equippedAmmo--;
                p.ammoBySlot[p.currentSlot] = p.equippedAmmo;
                p.shootCooldownSeconds = 1f / spec.fireRate;

                // Auto-start reload when last bullet fired and mags remain
                if (p.equippedAmmo == 0 && p.equippedMags > 0) {
                    startReload(p);
                }
            }

            // Trap damage — delegated to PlayerSystem so kill credit and kill-feed work
            if (state.isTrapAtWorld(p.pos.x, p.pos.y)) {
                playerSystem.takeDamage(p, 10f * dt, null);
            }
        }

        projectileSystem.update(dt);
    }

    // ── Reload ───────────────────────────────────────────────────────────────

    private void tickReload(PlayerState p, InputMessage in, float dt) {
        if (p.isReloading) {
            p.reloadTimer -= dt;
            if (p.reloadTimer <= 0f) finishReload(p);
            return;
        }
        // Manual reload (R key) — only if not full and has a spare mag
        if (in != null && in.reload && in.seq > p.lastReloadSeq
                && p.equippedMags > 0 && p.equippedAmmo < weaponRegistry.get(p.equippedWeaponType).maxAmmo) {
            p.lastReloadSeq = in.seq;
            startReload(p);
        }
    }

    private void startReload(PlayerState p) {
        p.isReloading = true;
        p.reloadTimer = weaponRegistry.get(p.equippedWeaponType).reloadSeconds;
    }

    private void finishReload(PlayerState p) {
        p.isReloading               = false;
        p.reloadTimer               = 0f;
        p.equippedMags--;
        p.magsBySlot[p.currentSlot] = p.equippedMags;
        p.equippedAmmo              = weaponRegistry.get(p.equippedWeaponType).maxAmmo;
        p.ammoBySlot[p.currentSlot] = p.equippedAmmo;
    }

    // ── Death drop ───────────────────────────────────────────────────────────

    private void dropLoot(PlayerState p) {
        int bestWeaponTier = 0;
        WeaponType bestWeapon    = null;
        int        bestWeaponAmmo = 0;
        int        bestWeaponMags = 0;

        for (int slot = 0; slot < 2; slot++) {
            WeaponType wt = p.inventory[slot];
            if (wt == null || weaponTier(wt) == 0) continue; // tier 0 (crossbow) never drops
            int t = weaponTier(wt);
            if (t > bestWeaponTier) {
                bestWeaponTier = t;
                bestWeapon     = wt;
                bestWeaponAmmo = Math.max(1, p.ammoBySlot[slot]);
                bestWeaponMags = p.magsBySlot[slot];
            }
        }

        int speedScore  = p.speedTier;
        int healthScore = p.healthTier / 2;

        if (bestWeaponTier == 0) {
            // Nothing worth dropping — always leave a heal
            state.pickups.add(new PickupState(UUID.randomUUID().toString(), PickupType.HEALTH,
                    new Vec2(p.pos.x, p.pos.y), 25, 0f, null, 0));
            return;
        }

        int best = Math.max(bestWeaponTier, Math.max(speedScore, healthScore));

        if (bestWeaponTier >= speedScore && bestWeaponTier >= healthScore && bestWeapon != null) {
            state.pickups.add(new PickupState(UUID.randomUUID().toString(), PickupType.WEAPON,
                    new Vec2(p.pos.x, p.pos.y), 0, 0f, bestWeapon, bestWeaponAmmo, bestWeaponMags));
        } else if (speedScore >= healthScore) {
            state.pickups.add(new PickupState(UUID.randomUUID().toString(), PickupType.SPEED,
                    new Vec2(p.pos.x, p.pos.y), 0, 0f, null, 0));
        } else {
            state.pickups.add(new PickupState(UUID.randomUUID().toString(), PickupType.HEALTH,
                    new Vec2(p.pos.x, p.pos.y), 40, 0f, null, 0));
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Right-hand perpendicular offset for bullet spawn — mirrors client rendering. */
    private static float spawnRightDist(WeaponType t) {
        if (t == null) return 0.70f;
        switch (t) {
            case CROSSBOW:     return 0.42f;
            case PISTOL:       return 0.84f;
            case UZI:          return 0.84f;
            case AK:           return 0.84f;
            case MACHINEGUN:   return 0.70f;
            case SHOTGUN:      return 0.70f;
            case SNIPER:       return 0.70f;
            case FLAMETHROWER: return 0.70f;
            default:           return 0.70f;
        }
    }

    /** Weapon tier for drop-ranking. Package-visible so ChestSystem can share it. */
    static int weaponTier(WeaponType t) {
        if (t == null) return 0;
        switch (t) {
            case CROSSBOW:                  return 0;
            case PISTOL:                    return 1;
            case UZI: case SHOTGUN:         return 2;
            case AK: case MACHINEGUN:       return 3;
            case SNIPER:                    return 4;
            case FLAMETHROWER:              return 5;
            default:                        return 1;
        }
    }

    private void spawnProjectiles(PlayerState p, WeaponSpec spec) {
        // Offset spawn to match the per-weapon visual barrel position
        float rd = spawnRightDist(p.equippedWeaponType);
        float rightOffX =  p.facing.y * rd;
        float rightOffY = -p.facing.x * rd;
        float sx = p.pos.x + p.facing.x * 1.55f + rightOffX;
        float sy = p.pos.y + p.facing.y * 1.55f + rightOffY;
        int   pellets = Math.max(1, spec.pellets);

        for (int k = 0; k < pellets; k++) {
            // Velocity from facing direction only — never from player velocity.
            float fx = p.facing.x;
            float fy = p.facing.y;

            if (spec.spreadRadians > 0f) {
                float angle  = (float) Math.atan2(fy, fx);
                float jitter = (float) (Math.random() * 2 - 1) * spec.spreadRadians;
                fx = (float) Math.cos(angle + jitter);
                fy = (float) Math.sin(angle + jitter);
            }

            state.projectiles.add(new ProjectileState(
                    UUID.randomUUID().toString(), p.playerId,
                    new Vec2(sx, sy),
                    new Vec2(fx * spec.projectileSpeed, fy * spec.projectileSpeed),
                    spec.damage, spec.projectileRadius, spec.ttlSeconds));
        }
    }
}
