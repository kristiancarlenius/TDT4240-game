package com.mygame.shared.dto;

import com.mygame.shared.util.Vec2;

public final class PickupDto {
    public String pickupId;
    public PickupType type;
    public Vec2 pos;

    public float healthAmount;

    public float speedboost;
    
    public WeaponDto weapon;

    public PickupDto() {}
}