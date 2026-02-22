package com.mygame.server.domain.model;

import com.mygame.shared.dto.WeaponType;

public final class WeaponSpec {
    public final WeaponType type;

    public final float damage;
    public final float projectileSpeed;
    public final float projectileRadius;
    public final float ttlSeconds;

    public final float fireRate;     // shots/sec
    public final int maxAmmo;

    public final int pellets;        // shotgun style (1 for most)
    public final float spreadRadians; // random spread cone

    public WeaponSpec(WeaponType type,
                      float damage,
                      float projectileSpeed,
                      float projectileRadius,
                      float ttlSeconds,
                      float fireRate,
                      int maxAmmo,
                      int pellets,
                      float spreadRadians) {
        this.type = type;
        this.damage = damage;
        this.projectileSpeed = projectileSpeed;
        this.projectileRadius = projectileRadius;
        this.ttlSeconds = ttlSeconds;
        this.fireRate = fireRate;
        this.maxAmmo = maxAmmo;
        this.pellets = pellets;
        this.spreadRadians = spreadRadians;
    }
}