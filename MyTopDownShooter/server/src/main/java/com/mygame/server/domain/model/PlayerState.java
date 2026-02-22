package com.mygame.server.domain.model;

import com.mygame.shared.dto.PlayerDto;
import com.mygame.shared.util.Vec2;

public final class PlayerState {
    public final String playerId;
    public final String username;

    public Vec2 pos;
    public Vec2 vel = Vec2.zero();
    public Vec2 facing = new Vec2(1f, 0f);

    public float hp = 100f;
    public float moveSpeed = 4.5f; // tiles/sec-ish if you treat 1 unit = 1 tile

    public float radius = 0.30f;           // player hit radius in world units (tile units)
    public float shootCooldownSeconds = 0f; // fire-rate limiter
    
    // weapon fields later
    public String equippedWeaponId = "pistol_t1";
    public int equippedAmmo = 999;

    public PlayerState(String playerId, String username, Vec2 spawnPos) {
        this.playerId = playerId;
        this.username = username;
        this.pos = spawnPos;
    }

    public PlayerDto toDto() {
        return new PlayerDto(
                playerId,
                username,
                pos,
                vel,
                facing,
                hp,
                moveSpeed,
                equippedWeaponId,
                equippedAmmo
        );
    }
}