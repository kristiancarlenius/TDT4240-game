package com.mygame.server.domain.system;

import com.mygame.server.data.weapon.WeaponRegistry;
import com.mygame.server.domain.model.ChestState;
import com.mygame.server.domain.model.PlayerState;
import com.mygame.server.domain.model.ServerGameState;
import com.mygame.shared.dto.PickupType;
import com.mygame.shared.dto.WeaponType;
import com.mygame.shared.util.Vec2;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Manages chest lifecycle:
 *  - Spawns a fixed number of chests at map-load time on random floor tiles.
 *  - Each tick: detects players touching closed chests and applies loot.
 *  - Respawns (resets loot) of opened chests after REOPEN_TIME seconds.
 *
 * No random floor pickups exist any more — chests are the only loot source
 * besides death drops.
 */
public final class ChestSystem {

    private static final int   CHEST_COUNT        = 6;
    private static final float INTERACT_RADIUS    = 0.65f;  // world units
    private static final float INTERACT_RADIUS_SQ = INTERACT_RADIUS * INTERACT_RADIUS;

    private final ServerGameState state;
    private final WeaponRegistry  weaponRegistry;
    private final Random          rng;

    public ChestSystem(ServerGameState state, WeaponRegistry weaponRegistry, Random rng) {
        this.state          = state;
        this.weaponRegistry = weaponRegistry;
        this.rng            = rng;
        spawnInitialChests();
    }

    // ── Tick ────────────────────────────────────────────────────────────────

    public void update(float dt) {
        for (ChestState chest : state.chests) {
            if (chest.isOpen) {
                chest.reopenTimer -= dt;
                if (chest.reopenTimer <= 0f) {
                    chest.isOpen = false;
                    rollLoot(chest);
                }
            } else {
                checkInteraction(chest);
            }
        }
    }

    // ── Private ─────────────────────────────────────────────────────────────

    private void spawnInitialChests() {
        for (int i = 0; i < CHEST_COUNT; i++) {
            Vec2       pos   = state.randomFloorTile(rng);
            ChestState chest = new ChestState(UUID.randomUUID().toString(), pos);
            rollLoot(chest);
            state.chests.add(chest);
        }
    }

    /** Check if any alive player is touching this closed chest. */
    private void checkInteraction(ChestState chest) {
        for (PlayerState p : state.players.values()) {
            if (p.isDead || p.pos == null) continue;
            float dx = p.pos.x - chest.pos.x;
            float dy = p.pos.y - chest.pos.y;
            if (dx * dx + dy * dy <= INTERACT_RADIUS_SQ) {
                openChest(chest, p);
                break; // only one player gets the loot
            }
        }
    }

    private void openChest(ChestState chest, PlayerState player) {
        chest.isOpen         = true;
        chest.reopenTimer    = ChestState.REOPEN_TIME;
        player.chestFreezeTimer = 0.2f;
        applyLoot(chest, player);
        System.out.println("[CHEST] " + player.username + " opened chest -> " + chest.lootType
                + (chest.lootWeapon != null ? " (" + chest.lootWeapon + ")" : ""));
    }

    // ── Loot application ─────────────────────────────────────────────────────

    private void applyLoot(ChestState chest, PlayerState p) {
        switch (chest.lootType) {
            case HEALTH: applyHealthLoot(p);       break;
            case SPEED:  applySpeedLoot(p);        break;
            case WEAPON: applyWeaponLoot(chest, p); break;
            case AMMO:   applyAmmoLoot(p);          break;
        }
    }

    private void applyAmmoLoot(PlayerState p) {
        if (p.equippedWeaponType == null) return;
        int tier    = ServerWorldStepSystem.weaponTier(p.equippedWeaponType);
        int magsAdd = (tier <= 3) ? 2 : 1;
        p.magsBySlot[p.currentSlot] += magsAdd;
        p.equippedMags               = p.magsBySlot[p.currentSlot];
        p.lastPickupNotice = "+" + magsAdd + " mag(s) for " + p.equippedWeaponType.name();
    }

    /**
     * Health chest logic:
     *  - Restore up to 50 HP (capped at maxHp).
     *  - If the amount that WOULD be restored is < 20 (player nearly full),
     *    upgrade maxHp by 10 instead (up to 200 / healthTier 10).
     */
    private void applyHealthLoot(PlayerState p) {
        float missing   = p.maxHp - p.hp;
        float wouldHeal = Math.min(50f, missing);

        if (wouldHeal < 20f && p.healthTier < PlayerState.MAX_HEALTH_TIER) {
            p.healthTier++;
            p.maxHp += 10f;
            // Also top up to new ceiling
            p.hp = Math.min(p.maxHp, p.hp + wouldHeal);
            p.lastPickupNotice = "Max HP +" + 10 + "  (now " + (int) p.maxHp + ")";
        } else {
            p.hp = Math.min(p.maxHp, p.hp + 50f);
            p.lastPickupNotice = "HP +" + (int) Math.min(50f, missing);
        }
    }

    /**
     * Speed chest logic:
     *  - Permanently increase speedTier by 1 (up to MAX_SPEED_TIER).
     *  - If already at max, give 20 HP instead.
     */
    private void applySpeedLoot(PlayerState p) {
        if (p.speedTier < PlayerState.MAX_SPEED_TIER) {
            p.speedTier++;
            p.moveSpeed        = PlayerState.speedForTier(p.speedTier);
            p.speedBoostTimer  = 0f; // clear any old temp boost
            p.lastPickupNotice = "Speed Tier " + p.speedTier + "!  (x"
                    + String.format("%.2f", 1f + p.speedTier * 0.25f) + " speed)";
        } else {
            p.hp = Math.min(p.maxHp, p.hp + 20f);
            p.lastPickupNotice = "Max Speed! +20 HP";
        }
    }

    private void applyWeaponLoot(ChestState chest, PlayerState p) {
        if (chest.lootWeapon == null) return;

        // Can't pick up same weapon twice
        for (WeaponType held : p.inventory) {
            if (chest.lootWeapon == held) {
                // Give ammo for the matching weapon instead
                int slot = (p.inventory[0] == chest.lootWeapon) ? 0 : 1;
                p.magsBySlot[slot] += 1;
                if (slot == p.currentSlot) p.equippedMags = p.magsBySlot[slot];
                p.lastPickupNotice = "+1 mag for " + chest.lootWeapon.name();
                return;
            }
        }

        int targetSlot = 1 - p.currentSlot;
        if (p.inventory[targetSlot] == null) {
            // Fill empty secondary slot
            p.inventory[targetSlot]  = chest.lootWeapon;
            p.ammoBySlot[targetSlot] = chest.lootAmmo;         // 1 loaded mag
            p.magsBySlot[targetSlot] = 1;                       // 1 spare mag = 2 total
            p.lastPickupNotice = "Got " + chest.lootWeapon.name()
                    + " (" + chest.lootAmmo + " ammo + 1 spare mag)";
        } else {
            // Both slots full: drop equipped, put chest weapon in equipped slot
            // (let PickupSpawnSystem handle if player walks over the drop;
            //  here we just add to secondary for simplicity — a chest forces secondary)
            p.inventory[targetSlot]  = chest.lootWeapon;
            p.ammoBySlot[targetSlot] = chest.lootAmmo;
            p.magsBySlot[targetSlot] = 1;
            p.lastPickupNotice = "Replaced secondary with " + chest.lootWeapon.name();
        }
    }

    // ── Loot generation ──────────────────────────────────────────────────────

    private void rollLoot(ChestState chest) {
        int roll = rng.nextInt(12);
        if (roll < 3) {
            chest.lootType   = PickupType.HEALTH;
            chest.lootWeapon = null;
            chest.lootAmmo   = 0;
        } else if (roll < 5) {
            chest.lootType   = PickupType.SPEED;
            chest.lootWeapon = null;
            chest.lootAmmo   = 0;
        } else if (roll < 7) {
            chest.lootType   = PickupType.AMMO;
            chest.lootWeapon = null;
            chest.lootAmmo   = 0;
        } else {
            chest.lootType   = PickupType.WEAPON;
            List<WeaponType> available = weaponRegistry.getSwitchOrder();
            // Exclude CROSSBOW (starter) from chest drops
            List<WeaponType> chestWeapons = available.stream()
                    .filter(w -> ServerWorldStepSystem.weaponTier(w) > 0)
                    .collect(java.util.stream.Collectors.toList());
            WeaponType wt = chestWeapons.isEmpty()
                    ? available.get(rng.nextInt(available.size()))
                    : chestWeapons.get(rng.nextInt(chestWeapons.size()));
            chest.lootWeapon = wt;
            chest.lootAmmo   = weaponRegistry.get(wt).maxAmmo;
        }
    }
}
