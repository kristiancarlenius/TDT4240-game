package com.mygame.server.domain.model;

import com.mygame.shared.dto.ChestDto;
import com.mygame.shared.dto.PickupType;
import com.mygame.shared.dto.WeaponType;
import com.mygame.shared.util.Vec2;

public final class ChestState {

    public final String chestId;
    public final Vec2   pos;

    public boolean   isOpen      = false;
    /** Seconds remaining before an opened chest is removed from the world. */
    public float     openTimer   = 0f;

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
