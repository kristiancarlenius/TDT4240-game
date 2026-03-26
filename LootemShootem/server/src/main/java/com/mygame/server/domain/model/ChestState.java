package com.mygame.server.domain.model;

import com.mygame.shared.dto.ChestDto;
import com.mygame.shared.dto.PickupType;
import com.mygame.shared.dto.WeaponType;
import com.mygame.shared.util.Vec2;

public final class ChestState {

    /** Seconds before an opened chest resets with fresh loot. */
    public static final float REOPEN_TIME = 30f;

    public final String chestId;
    public final Vec2   pos;

    public boolean   isOpen      = false;
    public float     reopenTimer = 0f;

    // Pre-generated loot (decided when chest spawns / resets, applied on open)
    public PickupType lootType;
    public WeaponType lootWeapon;   // non-null only when lootType == WEAPON
    public int        lootAmmo;

    public ChestState(String chestId, Vec2 pos) {
        this.chestId = chestId;
        this.pos     = pos;
    }

    public ChestDto toDto() {
        return new ChestDto(chestId, pos, isOpen);
    }
}
