package com.mygame.shared.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mygame.shared.util.Vec2;

public final class PlayerDto {
    public String playerId;
    public String username;

    public Vec2 pos;
    public Vec2 vel;
    public Vec2 facing;

    public float hp;
    public float moveSpeed;

    public WeaponType equippedWeaponType;
    public int equippedAmmo;

    public int score;
    public float timeSurvived;
    public boolean isDead;
    public float respawnTimer;
    public float speedBoostTimer;

    // Secondary weapon slot (null = empty)
    public WeaponType secondaryWeaponType;
    public int        secondaryAmmo;
    public int        secondaryMags;

    /** Spare magazines for the equipped weapon. */
    public int     equippedMags;
    /** True while a reload animation is in progress. */
    public boolean isReloading;
    /** Seconds remaining in the current reload (0 when not reloading). */
    public float   reloadTimer;

    public String lastPickupNotice;

    /** Permanent speed tier (0–5). Each tier adds 15% base speed. */
    public int   speedTier;
    /** Max-HP upgrade level (0–10). Each level adds +10 to maxHp. */
    public int   healthTier;
    /** Current max HP (100 + healthTier * 10). */
    public float maxHp;

    /** True if the player recently took damage (for visual feedback). */
    @JsonProperty("isHurt")
    public boolean isHurt;
    /** True if the player recently received a significant heal (for visual feedback). */
    @JsonProperty("isHealed")
    public boolean isHealed;

    /** Kills accumulated since the last respawn (resets to 0 on death). */
    public int killsThisLife;

    public PlayerDto() {}

    public PlayerDto(String playerId, String username, Vec2 pos, Vec2 vel, Vec2 facing,
                     float hp, float moveSpeed, WeaponType equippedWeaponType, int equippedAmmo) {
        this.playerId = playerId;
        this.username = username;
        this.pos = pos;
        this.vel = vel;
        this.facing = facing;
        this.hp = hp;
        this.moveSpeed = moveSpeed;
        this.equippedWeaponType = equippedWeaponType;
        this.equippedAmmo = equippedAmmo;
    }
}
