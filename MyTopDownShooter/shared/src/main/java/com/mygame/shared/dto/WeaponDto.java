package com.mygame.shared.dto;

public final class WeaponDto {
    public String weaponId;        // stable id, e.g. "pistol_t1"
    public float damage;
    public float projectileSpeed;
    public float projectileRadius; // size/spread proxy (you can later separate spread vs radius)
    public int ammo;
    public int tier;

    // Jackson needs a no-arg constructor
    public WeaponDto() {}

    public WeaponDto(String weaponId, float damage, float projectileSpeed, float projectileRadius, int ammo, int tier) {
        this.weaponId = weaponId;
        this.damage = damage;
        this.projectileSpeed = projectileSpeed;
        this.projectileRadius = projectileRadius;
        this.ammo = ammo;
        this.tier = tier;
    }
}