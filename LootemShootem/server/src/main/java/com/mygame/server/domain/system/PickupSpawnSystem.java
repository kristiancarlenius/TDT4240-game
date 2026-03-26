package com.mygame.server.domain.system;

import com.mygame.server.data.weapon.WeaponRegistry;
import com.mygame.server.domain.model.PickupState;
import com.mygame.server.domain.model.PlayerState;
import com.mygame.server.domain.model.ServerGameState;
import com.mygame.shared.dto.PickupType;
import com.mygame.shared.dto.WeaponType;
import com.mygame.shared.util.Vec2;

import java.util.UUID;

/**
 * Handles collection of ground pickups (death drops only — no random spawning).
 *
 * Weapon pickup rules:
 *  - Cannot pick up the same weapon type you already carry.
 *  - If your secondary slot is empty the weapon fills it silently.
 *  - Otherwise the picked-up weapon replaces the EQUIPPED weapon; old weapon drops to ground.
 *
 * Ammo pickup:
 *  - Gives spare magazines for the weapon you are currently holding.
 *  - Tier 1–3: +2 mags.  Tier 4–5: +1 mag.
 */
public final class PickupSpawnSystem {

    private static final float COLLECT_RADIUS    = 0.32f;
    private static final float COLLECT_RADIUS_SQ = COLLECT_RADIUS * COLLECT_RADIUS;

    private final ServerGameState state;
    private final WeaponRegistry  weaponRegistry;

    public PickupSpawnSystem(ServerGameState state, WeaponRegistry weaponRegistry) {
        this.state          = state;
        this.weaponRegistry = weaponRegistry;
    }

    public void update() {
        for (PlayerState p : state.players.values()) {
            if (!p.isDead) collectPickups(p);
        }
    }

    // ── Collection ───────────────────────────────────────────────────────────

    private void collectPickups(PlayerState p) {
        state.pickups.removeIf(pickup -> {
            if (pickup.pos == null) return true;
            float dx = p.pos.x - pickup.pos.x;
            float dy = p.pos.y - pickup.pos.y;
            if (dx * dx + dy * dy > COLLECT_RADIUS_SQ) return false;
            applyPickup(p, pickup);
            return true;
        });
    }

    // ── Pickup application ────────────────────────────────────────────────────

    private void applyPickup(PlayerState p, PickupState pickup) {
        switch (pickup.type) {
            case HEALTH: applyHealth(p, pickup); break;
            case SPEED:  applySpeed(p);          break;
            case WEAPON: applyWeapon(p, pickup);  break;
            case AMMO:   applyAmmo(p);            break;
        }
        System.out.println("[PICKUP] " + p.username + " collected " + pickup.type);
    }

    private void applyHealth(PlayerState p, PickupState pickup) {
        float missing   = p.maxHp - p.hp;
        float wouldHeal = Math.min(50f, missing);
        if (wouldHeal < 20f && p.healthTier < PlayerState.MAX_HEALTH_TIER) {
            p.healthTier++;
            p.maxHp += 10f;
            p.hp     = Math.min(p.maxHp, p.hp + wouldHeal);
            p.lastPickupNotice = "Max HP +" + 10 + "  (now " + (int) p.maxHp + ")";
        } else {
            int healed = (int) Math.min(Math.max(pickup.healthAmount, 25f), missing);
            p.hp = Math.min(p.maxHp, p.hp + healed);
            p.lastPickupNotice = "HP +" + healed;
        }
    }

    private void applySpeed(PlayerState p) {
        if (p.speedTier < PlayerState.MAX_SPEED_TIER) {
            p.speedTier++;
            p.moveSpeed       = PlayerState.speedForTier(p.speedTier);
            p.speedBoostTimer = 0f;
            p.lastPickupNotice = "Speed Tier " + p.speedTier + "!";
        } else {
            p.hp = Math.min(p.maxHp, p.hp + 20f);
            p.lastPickupNotice = "Max Speed! +20 HP";
        }
    }

    /**
     * Weapon pickup logic:
     *  1. Already have that type → ignore (can't carry same weapon twice).
     *  2. Secondary slot empty → fill it silently.
     *  3. Both full → replace equipped; old weapon drops to ground.
     */
    private void applyWeapon(PlayerState p, PickupState pickup) {
        WeaponType incoming = pickup.weaponType;
        if (incoming == null) return;

        // Rule 1: no duplicates
        for (WeaponType held : p.inventory) {
            if (incoming == held) {
                p.lastPickupNotice = "Already carrying " + incoming.name();
                return;
            }
        }

        int secSlot = 1 - p.currentSlot;

        // Rule 2: fill empty slot
        if (p.inventory[secSlot] == null) {
            p.inventory[secSlot]  = incoming;
            p.ammoBySlot[secSlot] = pickup.ammoAmount > 0
                    ? pickup.ammoAmount : weaponRegistry.get(incoming).maxAmmo;
            p.magsBySlot[secSlot] = pickup.magsAmount;
            p.lastPickupNotice    = "Got " + incoming.name();
            return;
        }

        // Rule 3: replace equipped, drop old equipped weapon on the ground
        WeaponType dropped     = p.inventory[p.currentSlot];
        int        droppedAmmo = p.ammoBySlot[p.currentSlot];
        int        droppedMags = p.magsBySlot[p.currentSlot];

        p.inventory[p.currentSlot]  = incoming;
        p.ammoBySlot[p.currentSlot] = pickup.ammoAmount > 0
                ? pickup.ammoAmount : weaponRegistry.get(incoming).maxAmmo;
        p.magsBySlot[p.currentSlot] = pickup.magsAmount;
        p.isReloading               = false;
        p.reloadTimer               = 0f;
        p.syncEquipped();

        state.pickups.add(new PickupState(
                UUID.randomUUID().toString(), PickupType.WEAPON,
                new Vec2(p.pos.x, p.pos.y),
                0, 0f, dropped, droppedAmmo, droppedMags));

        p.lastPickupNotice = "Swapped to " + incoming.name()
                + " (dropped " + (dropped != null ? dropped.name() : "") + ")";
    }

    /**
     * Ammo pickup: adds spare magazines for the weapon you're holding.
     * Tier 1–3 → +2 mags; tier 4–5 → +1 mag.
     */
    private void applyAmmo(PlayerState p) {
        if (p.equippedWeaponType == null) return;
        int tier    = ServerWorldStepSystem.weaponTier(p.equippedWeaponType);
        int magsAdd = (tier <= 3) ? 2 : 1;
        p.magsBySlot[p.currentSlot] += magsAdd;
        p.equippedMags               = p.magsBySlot[p.currentSlot];
        p.lastPickupNotice = "+" + magsAdd + " mag(s) for " + p.equippedWeaponType.name();
    }
}
