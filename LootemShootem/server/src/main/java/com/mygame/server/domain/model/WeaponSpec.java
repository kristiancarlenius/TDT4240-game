package com.mygame.server.domain.model;

import com.mygame.shared.dto.WeaponType;

public final class WeaponSpec {
    public final WeaponType type;
    public final int        tier;           // 1–5 used for drop ranking

    public final float damage;
    public final float projectileSpeed;
    public final float projectileRadius;
    public final float ttlSeconds;

    public final float fireRate;            // shots / second
    public final int   maxAmmo;             // bullets per magazine
    public final int   numMagazines;        // spare mags given on pickup (not counting loaded)

    public final int   pellets;             // 1 for most, >1 for shotgun / flamethrower
    public final float spreadRadians;

    public final float reloadSeconds;       // time to swap in a new magazine

    public WeaponSpec(WeaponType type, int tier,
                      float damage,
                      float projectileSpeed, float projectileRadius, float ttlSeconds,
                      float fireRate,
                      int maxAmmo, int numMagazines,
                      int pellets, float spreadRadians,
                      float reloadSeconds) {
        this.type             = type;
        this.tier             = tier;
        this.damage           = damage;
        this.projectileSpeed  = projectileSpeed;
        this.projectileRadius = projectileRadius;
        this.ttlSeconds       = ttlSeconds;
        this.fireRate         = fireRate;
        this.maxAmmo          = maxAmmo;
        this.numMagazines     = numMagazines;
        this.pellets          = pellets;
        this.spreadRadians    = spreadRadians;
        this.reloadSeconds    = reloadSeconds;
    }
}
