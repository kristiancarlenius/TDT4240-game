package com.mygame.server.domain.model;

import com.mygame.shared.dto.PickupDto;
import com.mygame.shared.dto.PickupType;
import com.mygame.shared.dto.WeaponType;
import com.mygame.shared.util.Vec2;

public final class PickupState {

    public final String      id;
    public final PickupType  type;
    public       Vec2        pos;

    public final int        healthAmount;
    public final float      speedBoostSeconds;
    public final WeaponType weaponType;   // null for non-weapon pickups
    public final int        ammoAmount;
    public final int        magsAmount;   // spare magazines bundled with weapon drop
    /** Ticks during which this pickup cannot be collected (anti-loop immunity). */
    public final long       immuneUntilTick;

    public PickupState(String id, PickupType type, Vec2 pos,
                       int healthAmount, float speedBoostSeconds,
                       WeaponType weaponType, int ammoAmount) {
        this(id, type, pos, healthAmount, speedBoostSeconds, weaponType, ammoAmount, 0, 0L);
    }

    public PickupState(String id, PickupType type, Vec2 pos,
                       int healthAmount, float speedBoostSeconds,
                       WeaponType weaponType, int ammoAmount, int magsAmount) {
        this(id, type, pos, healthAmount, speedBoostSeconds, weaponType, ammoAmount, magsAmount, 0L);
    }

    public PickupState(String id, PickupType type, Vec2 pos,
                       int healthAmount, float speedBoostSeconds,
                       WeaponType weaponType, int ammoAmount, int magsAmount, long immuneUntilTick) {
        this.id                = id;
        this.type              = type;
        this.pos               = pos;
        this.healthAmount      = healthAmount;
        this.speedBoostSeconds = speedBoostSeconds;
        this.weaponType        = weaponType;
        this.ammoAmount        = ammoAmount;
        this.magsAmount        = magsAmount;
        this.immuneUntilTick   = immuneUntilTick;
    }

    public PickupDto toDto() {
        return new PickupDto(id, type, pos, healthAmount, speedBoostSeconds, weaponType, ammoAmount);
    }
}
