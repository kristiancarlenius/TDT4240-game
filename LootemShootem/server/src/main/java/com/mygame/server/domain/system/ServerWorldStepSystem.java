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
    private static final float CHAR_SIZE = 1.72f;
    private static final float[][][] WEAPON_HAND_ANCHORS = new float[][][] {
            {
                    { 0.04f, -0.05f },
                    { -0.18f, -0.02f },
                    { 0.06f, 0.02f },
                    { 0.20f, -0.02f }
            },
            {
                    { 0.05f, -0.05f },
                    { -0.17f, -0.01f },
                    { 0.07f, 0.03f },
                    { 0.21f, -0.01f }
            },
            {
                    { 0.04f, -0.04f },
                    { -0.19f, -0.01f },
                    { 0.05f, 0.02f },
                    { 0.19f, -0.01f }
            },
            {
                    { 0.05f, -0.04f },
                    { -0.18f, -0.02f },
                    { 0.06f, 0.03f },
                    { 0.20f, -0.02f }
            }
    };

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
        pickupSpawnSystem.update(state.tick);
        playerSystem.update(dt);
        projectileSystem.update(dt);

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
            Vec2 moveVec;
            if (p.chestFreezeTimer > 0f) {
                p.chestFreezeTimer -= dt;
                collisionSystem.applyMovement(p, Vec2.zero(), aim, dt);
                moveVec = Vec2.zero();
            } else {
                moveVec = (in != null && in.move != null) ? in.move : Vec2.zero();
                collisionSystem.applyMovement(p, moveVec, aim, dt);
            }

            // Update dominant movement direction (for 4-way sprite orientation)
            float mx = moveVec.x, my = moveVec.y;
            if (Math.abs(mx) > 0.1f || Math.abs(my) > 0.1f) {
                if (Math.abs(mx) >= Math.abs(my)) {
                    p.moveDir = mx > 0 ? 3 : 1; // RIGHT or LEFT
                } else {
                    p.moveDir = my > 0 ? 2 : 0; // UP or DOWN
                }
            }
            // When idle (moveVec ≈ zero), moveDir stays at its last value — body keeps facing that direction.

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
        System.out.println("[DROP] dropLoot called for " + p.username + " at " + p.pos);
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

        // 3 ticks immunity (0.15s at 20Hz) — just enough to avoid same-tick self-collection
        long immune = state.tick + 3;

        if (bestWeaponTier == 0) {
            state.pickups.add(new PickupState(UUID.randomUUID().toString(), PickupType.HEALTH,
                    new Vec2(p.pos.x, p.pos.y), 25, 0f, null, 0, 0, immune));
            System.out.println("[DROP] spawned HEALTH pickup at " + p.pos + "  total pickups=" + state.pickups.size());
            return;
        }

        if (bestWeaponTier >= speedScore && bestWeaponTier >= healthScore && bestWeapon != null) {
            state.pickups.add(new PickupState(UUID.randomUUID().toString(), PickupType.WEAPON,
                    new Vec2(p.pos.x, p.pos.y), 0, 0f, bestWeapon, bestWeaponAmmo, bestWeaponMags, immune));
            System.out.println("[DROP] spawned WEAPON(" + bestWeapon + ") pickup at " + p.pos + "  total pickups=" + state.pickups.size());
        } else if (speedScore >= healthScore) {
            state.pickups.add(new PickupState(UUID.randomUUID().toString(), PickupType.SPEED,
                    new Vec2(p.pos.x, p.pos.y), 0, 0f, null, 0, 0, immune));
            System.out.println("[DROP] spawned SPEED pickup at " + p.pos + "  total pickups=" + state.pickups.size());
        } else {
            state.pickups.add(new PickupState(UUID.randomUUID().toString(), PickupType.HEALTH,
                    new Vec2(p.pos.x, p.pos.y), 40, 0f, null, 0, 0, immune));
            System.out.println("[DROP] spawned HEALTH(40) pickup at " + p.pos + "  total pickups=" + state.pickups.size());
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Right-hand perpendicular offset for bullet spawn — mirrors client rendering. */
    private static WeaponRenderSpec weaponRenderSpec(WeaponType t) {
        if (t == null) return new WeaponRenderSpec(0f, 0f, 1.55f, 0f);
        switch (t) {
            case CROSSBOW:     return new WeaponRenderSpec(0.03f, -0.01f, 1.746f, 0.700f);
            case PISTOL:       return new WeaponRenderSpec(0.02f, -0.05f, 1.746f, 0.502f);
            case UZI:          return new WeaponRenderSpec(0.02f, -0.04f, 1.746f, 0.184f);
            case AK:           return new WeaponRenderSpec(0.03f, -0.04f, 1.653f, 0.132f);
            case MACHINEGUN:   return new WeaponRenderSpec(0.04f, -0.02f, 1.742f, 0.330f);
            case SHOTGUN:      return new WeaponRenderSpec(0.03f, -0.03f, 1.746f, 0.094f);
            case SNIPER:       return new WeaponRenderSpec(0.04f, -0.04f, 1.560f, 0.105f);
            case FLAMETHROWER: return new WeaponRenderSpec(0.03f, -0.03f, 1.746f, 0.020f);
            default:           return new WeaponRenderSpec(0f, 0f, 1.55f, 0f);
        }
    }

    private static float[] resolveHandAnchor(PlayerState p) {
        int skinId = Math.max(0, Math.min(3, p.skinId));
        int dir = (p.moveDir >= 0 && p.moveDir < 4) ? p.moveDir : 2;
        float[] anchor = WEAPON_HAND_ANCHORS[skinId][dir];
        if (p.facing != null && Math.abs(p.facing.x) > Math.abs(p.facing.y)) {
            int oppDir = p.facing.x >= 0f ? 1 : 3;
            return new float[]{ WEAPON_HAND_ANCHORS[skinId][oppDir][0], anchor[1] };
        }
        return anchor;
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
        WeaponRenderSpec renderSpec = weaponRenderSpec(p.equippedWeaponType);
        float[] handAnchor = resolveHandAnchor(p);
        float rightX = p.facing.y;
        float rightY = -p.facing.x;
        float handX = p.pos.x + (handAnchor[0] + renderSpec.handOffsetX) * CHAR_SIZE;
        float handY = p.pos.y + (handAnchor[1] + renderSpec.handOffsetY) * CHAR_SIZE;

        // Perpendicular barrel offset: flip sign when facing left because the sprite Y is
        // mirrored vertically (flipY=true on client), which reverses the side direction.
        float flipSign = p.facing.x < 0f ? -1f : 1f;
        float sx = handX;
        float sy = handY;
        boolean foundSpawn = false;
        for (float factor = 1f; factor >= 0f; factor -= 0.2f) {
            float candidateX = handX + p.facing.x * (renderSpec.muzzleForward * factor) + rightX * renderSpec.muzzleSide * flipSign;
            float candidateY = handY + p.facing.y * (renderSpec.muzzleForward * factor) + rightY * renderSpec.muzzleSide * flipSign;
            if (!state.isProjectileBlockedWorld(candidateX, candidateY)) {
                sx = candidateX;
                sy = candidateY;
                foundSpawn = true;
                break;
            }
        }
        if (!foundSpawn) return;

        int pellets = Math.max(1, spec.pellets);
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
                    UUID.randomUUID().toString(), p.playerId,
                    new Vec2(sx, sy),
                    new Vec2(fx * spec.projectileSpeed, fy * spec.projectileSpeed),
                    spec.damage, spec.projectileRadius, spec.ttlSeconds));
        }
    }

    private static final class WeaponRenderSpec {
        final float handOffsetX;
        final float handOffsetY;
        final float muzzleForward;
        final float muzzleSide;

        WeaponRenderSpec(float handOffsetX, float handOffsetY, float muzzleForward, float muzzleSide) {
            this.handOffsetX = handOffsetX;
            this.handOffsetY = handOffsetY;
            this.muzzleForward = muzzleForward;
            this.muzzleSide = muzzleSide;
        }
    }
}
