package com.mygame.shared.dto;

import com.mygame.shared.util.Vec2;

public final class PlayerDto {
    public String playerId;
    public String username;

    public Vec2 pos;
    public Vec2 vel;

    // normalized aim direction (or zero)
    public Vec2 facing;

    public float hp;
    public float moveSpeed;

    public String equippedWeaponId;
    public int equippedAmmo;

    public PlayerDto() {}

    public PlayerDto(String playerId, String username, Vec2 pos, Vec2 vel, Vec2 facing,
                     float hp, float moveSpeed, String equippedWeaponId, int equippedAmmo) {
        this.playerId = playerId;
        this.username = username;
        this.pos = pos;
        this.vel = vel;
        this.facing = facing;
        this.hp = hp;
        this.moveSpeed = moveSpeed;
        this.equippedWeaponId = equippedWeaponId;
        this.equippedAmmo = equippedAmmo;
    }
}