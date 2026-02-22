package com.mygame.shared.dto;

import com.mygame.shared.util.Vec2;

public final class PickupDto {
    public String pickupId;
    public PickupType type;
    public Vec2 pos;

    // payload (only some are used depending on type)
    public int healthAmount;         // e.g. for HEALTH pickup
    public float speedBoostSeconds;  // e.g. for SPEED pickup
    public WeaponType weaponType;    // e.g. for WEAPON pickup
    public int ammoAmount;           // e.g. for AMMO pickup

    // Jackson needs a no-arg constructor
    public PickupDto() {}

    public PickupDto(String pickupId, PickupType type, Vec2 pos,
                     int healthAmount, float speedBoostSeconds,
                     WeaponType weaponType, int ammoAmount) {
        this.pickupId = pickupId;
        this.type = type;
        this.pos = pos;
        this.healthAmount = healthAmount;
        this.speedBoostSeconds = speedBoostSeconds;
        this.weaponType = weaponType;
        this.ammoAmount = ammoAmount;
    }
}