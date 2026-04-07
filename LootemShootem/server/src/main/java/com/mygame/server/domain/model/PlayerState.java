package com.mygame.server.domain.model;

import com.mygame.shared.dto.PlayerDto;
import com.mygame.shared.dto.WeaponType;
import com.mygame.shared.util.Vec2;

public final class PlayerState {
    public final String playerId;
    public final String username;

    public Vec2 pos;
    public Vec2 vel    = Vec2.zero();
    public Vec2 facing = new Vec2(1f, 0f);

    public static final float BASE_MOVE_SPEED  = 4.5f;
    public static final int   MAX_SPEED_TIER   = 10;
    public static final int   MAX_HEALTH_TIER  = 10;

    public int   speedTier  = 0;
    public int   healthTier = 0;
    public float maxHp      = 100f;

    public static float speedForTier(int tier) {
        return BASE_MOVE_SPEED * (1f + tier * 0.25f);
    }

    public float hp        = 100f;
    public float moveSpeed = BASE_MOVE_SPEED;
    public float radius    = 0.60f;

    // ── Weapons ──────────────────────────────────────────────────────────────
    public WeaponType[] inventory  = new WeaponType[2];
    public int[]        ammoBySlot = new int[2];
    public int[]        magsBySlot = new int[2]; // spare magazines per slot
    public int          currentSlot = 0;

    // Mirrors of the equipped slot — kept in sync by syncEquipped()
    public WeaponType equippedWeaponType = WeaponType.CROSSBOW;
    public int        equippedAmmo       = 0;
    public int        equippedMags       = 0;

    // ── Reload ───────────────────────────────────────────────────────────────
    public boolean isReloading  = false;
    public float   reloadTimer  = 0f;

    // ── Combat ───────────────────────────────────────────────────────────────
    public float shootCooldownSeconds = 0f;
    public int   lastSwitchSeq        = 0;
    public int   lastReloadSeq        = 0;
    public float hurtTimer            = 0f;
    public float healTimer            = 0f;

    // ── Stats ────────────────────────────────────────────────────────────────
    public int     score          = 0;
    public int     killsThisLife  = 0;
    public float   timeSurvived   = 0f;
    public boolean isDead       = false;
    public boolean justDied     = false;
    public float   respawnTimer = 0f;

    /** Legacy — kept so existing HUD code compiles; timed boosts removed. */
    public float speedBoostTimer = 0f;

    /** Movement freeze after opening a chest (seconds). */
    public float chestFreezeTimer = 0f;

    /** Skin index (0–3) chosen by the player at login. */
    public int skinId = 0;

    /**
     * Dominant movement direction: 0=DOWN, 1=LEFT, 2=UP, 3=RIGHT, -1=idle.
     * Updated every tick from vel; persists so idle pose keeps last direction.
     */
    public int moveDir = 2; // default UP (facing camera on spawn)

    public String lastPickupNotice;

    public PlayerState(String playerId, String username, Vec2 spawnPos) {
        this.playerId = playerId;
        this.username = username;
        this.pos      = spawnPos;
        inventory[0]  = WeaponType.CROSSBOW;
        inventory[1]  = null;
    }

    /** Sync the equipped-slot mirrors after any slot mutation. */
    public void syncEquipped() {
        equippedWeaponType = inventory[currentSlot];
        equippedAmmo       = ammoBySlot[currentSlot];
        equippedMags       = magsBySlot[currentSlot];
    }

    public PlayerDto toDto() {
        PlayerDto dto = new PlayerDto(
                playerId, username, pos, vel, facing,
                hp, moveSpeed, equippedWeaponType, equippedAmmo);
        dto.score               = score;
        dto.timeSurvived        = timeSurvived;
        dto.isDead              = isDead;
        dto.respawnTimer        = respawnTimer;
        dto.speedBoostTimer     = speedBoostTimer;
        dto.speedTier           = speedTier;
        dto.healthTier          = healthTier;
        dto.maxHp               = maxHp;
        dto.equippedMags        = equippedMags;
        dto.isReloading         = isReloading;
        dto.reloadTimer         = reloadTimer;
        dto.isHurt              = hurtTimer > 0f;
        dto.isHealed            = healTimer > 0f;
        dto.killsThisLife       = killsThisLife;
        int sec = 1 - currentSlot;
        dto.secondaryWeaponType = inventory[sec];
        dto.secondaryAmmo       = ammoBySlot[sec];
        dto.secondaryMags       = magsBySlot[sec];
        dto.lastPickupNotice    = lastPickupNotice;
        dto.skinId              = skinId;
        dto.moveDir             = moveDir;
        return dto;
    }
}
