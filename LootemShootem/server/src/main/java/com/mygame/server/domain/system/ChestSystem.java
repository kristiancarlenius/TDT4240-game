package com.mygame.server.domain.system;

import com.mygame.server.data.weapon.WeaponRegistry;
import com.mygame.server.domain.model.ChestState;
import com.mygame.server.domain.model.PlayerState;
import com.mygame.server.domain.model.ServerGameState;
import com.mygame.shared.dto.PickupType;
import com.mygame.shared.dto.WeaponType;
import com.mygame.shared.dto.TileType;
import com.mygame.shared.util.Vec2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.UUID;

/**
 * Manages chest lifecycle.
 *
 * Chests are treated as a bounded pool of spawn spots:
 *  - spawn from the authored chest-point list
 *  - disappear when looted
 *  - respawn later on a free spot chosen from the same pool
 *
 * This keeps the number of active chests bounded while still randomizing where
 * the next chest appears.
 */
public final class ChestSystem {

    private static final int   DEFAULT_CHEST_COUNT   = 6;
    private static final float INTERACT_RADIUS       = 0.65f;  // world units
    private static final float INTERACT_RADIUS_SQ    = INTERACT_RADIUS * INTERACT_RADIUS;
    private static final float RESPAWN_MIN_SECONDS   = 20f;
    private static final float RESPAWN_MAX_SECONDS   = 40f;
    private static final float RESPAWN_RETRY_SECONDS = 0.5f;

    private final ServerGameState state;
    private final WeaponRegistry  weaponRegistry;
    private final Random          rng;
    private final List<Vec2>       spawnCandidates;
    // Spawn-point pool that is currently available for a chest.
    private final List<Vec2>       freeSpawnPoints = new ArrayList<>();
    // O(1) lookup for removing a spawn point from the free list.
    private final Map<String, Integer> freeSpawnIndexByKey = new HashMap<>();
    // Single respawn queue shared by all chests.
    private final PriorityQueue<RespawnEvent> respawnQueue = new PriorityQueue<>(Comparator.comparingDouble(e -> e.dueTime));
    private final int             initialChestCount;
    private float                  elapsedSeconds = 0f;

    private static final class RespawnEvent {
        final float dueTime;

        RespawnEvent(float dueTime) {
            this.dueTime = dueTime;
        }
    }

    public ChestSystem(ServerGameState state, WeaponRegistry weaponRegistry, Random rng) {
        this(state, weaponRegistry, rng, DEFAULT_CHEST_COUNT);
    }

    public ChestSystem(ServerGameState state, WeaponRegistry weaponRegistry, Random rng, int chestCount) {
        this.state          = state;
        this.weaponRegistry = weaponRegistry;
        this.rng            = rng;
        this.spawnCandidates = buildSpawnCandidates();
        // chestCount is the hard cap; if the map exposes fewer valid spots,
        // we clamp to the number of available candidates.
        this.initialChestCount = Math.min(Math.max(0, chestCount), spawnCandidates.size());
        resetFreeSpawnPoints();
        spawnInitialChests();
    }

    // ── Tick ────────────────────────────────────────────────────────────────

    /** Advances the respawn queue and checks all currently active chests. */
    public void update(float dt) {
        elapsedSeconds += dt;
        updateRespawns();

        for (ChestState chest : new ArrayList<>(state.chests)) {
            if (chest.isOpen) {
                chest.openTimer -= dt;
                if (chest.openTimer <= 0f) {
                    state.chests.remove(chest);
                }
            } else {
                checkInteraction(chest);
            }
        }
    }

    // ── Private ─────────────────────────────────────────────────────────────

    /** Spawns the initial active chest set. */
    private void spawnInitialChests() {
        for (int i = 0; i < initialChestCount; i++) {
            if (!spawnChest()) {
                break;
            }
        }
    }

    /** Processes only the respawns that are due on this tick. */
    private void updateRespawns() {
        while (!respawnQueue.isEmpty() && respawnQueue.peek().dueTime <= elapsedSeconds) {
            respawnQueue.poll();
            if (!spawnChest()) {
                respawnQueue.add(new RespawnEvent(elapsedSeconds + RESPAWN_RETRY_SECONDS));
                break;
            }
        }
    }

    private boolean spawnChest() {
        Vec2 pos = takeRandomFreeSpawnPoint();
        if (pos == null) {
            return false;
        }

        ChestState chest = new ChestState(UUID.randomUUID().toString(), pos);
        rollLoot(chest);
        state.chests.add(chest);
        return true;
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
        player.chestFreezeTimer = 0.2f;
        applyLoot(chest, player);
        chest.isOpen    = true;
        chest.openTimer = 2f;   // chest stays visible as open for 2 seconds
        addFreeSpawnPoint(chest.pos);
        // Respawn is decoupled from the chest instance; we only schedule a new
        // spawn event and let the queue decide when to create it.
        respawnQueue.add(new RespawnEvent(elapsedSeconds + randomRespawnDelay()));

        System.out.println("[CHEST] " + player.username + " looted chest -> " + chest.lootType
                + (chest.lootWeapon != null ? " (" + chest.lootWeapon + ")" : ""));
    }

    private float randomRespawnDelay() {
        return RESPAWN_MIN_SECONDS + rng.nextFloat() * (RESPAWN_MAX_SECONDS - RESPAWN_MIN_SECONDS);
    }

    /** Collects all legal chest spawn points for this map. */
    private List<Vec2> buildSpawnCandidates() {
        if (!state.chestSpawnPoints.isEmpty()) {
            return new ArrayList<>(state.chestSpawnPoints);
        }

        List<Vec2> candidates = new ArrayList<>();
        for (int y = 1; y < state.height - 1; y++) {
            for (int x = 1; x < state.width - 1; x++) {
                if (state.tiles[y * state.width + x] == TileType.FLOOR) {
                    candidates.add(new Vec2(x + 0.5f, y + 0.5f));
                }
            }
        }
        return candidates;
    }

    /** Rebuilds the free-spawn pool from the full candidate list. */
    private void resetFreeSpawnPoints() {
        freeSpawnPoints.clear();
        freeSpawnIndexByKey.clear();
        for (Vec2 pos : spawnCandidates) {
            addFreeSpawnPoint(pos);
        }
    }

    /** Removes one random free spawn point in O(1). */
    private Vec2 takeRandomFreeSpawnPoint() {
        if (freeSpawnPoints.isEmpty()) {
            return null;
        }

        int index = rng.nextInt(freeSpawnPoints.size());
        Vec2 pos = freeSpawnPoints.get(index);

        int lastIndex = freeSpawnPoints.size() - 1;
        Vec2 last = freeSpawnPoints.get(lastIndex);
        freeSpawnPoints.set(index, last);
        freeSpawnIndexByKey.put(spawnKey(last), index);
        freeSpawnPoints.remove(lastIndex);
        freeSpawnIndexByKey.remove(spawnKey(pos));

        return pos;
    }

    /** Returns a spawn point to the free pool if it is not already present. */
    private void addFreeSpawnPoint(Vec2 pos) {
        String key = spawnKey(pos);
        if (freeSpawnIndexByKey.containsKey(key)) {
            return;
        }
        freeSpawnIndexByKey.put(key, freeSpawnPoints.size());
        freeSpawnPoints.add(pos);
    }

    /** Uses tile coordinates so mirrored floats still map to the same spot. */
    private static String spawnKey(Vec2 pos) {
        return ((int) Math.floor(pos.x)) + ":" + ((int) Math.floor(pos.y));
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
            p.hp = p.maxHp; // fill to new max immediately
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
