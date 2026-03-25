package com.mygame.server.domain.model;

import com.mygame.shared.dto.PlayerDto;
import com.mygame.shared.util.Vec2;
import com.mygame.shared.dto.WeaponType;

public final class PlayerState {
    public final String playerId;
    public final String username;

    public Vec2 pos;
    public Vec2 vel = Vec2.zero();
    public Vec2 facing = new Vec2(1f, 0f);

    public static final float BASE_MOVE_SPEED = 4.5f;

    public float hp = 100f;
    public float moveSpeed = BASE_MOVE_SPEED;

    public float radius = 0.30f;           // player hit radius in world units (tile units)
    public float shootCooldownSeconds = 0f; // fire-rate limiter

    public WeaponType equippedWeaponType = WeaponType.CROSSBOW; // start weapon
    public int equippedAmmo = 25; // will be set from registry on join
    public int lastSwitchSeq = 0; // edge-detect switchWeapon

    public int score = 0;
    public boolean isDead = false;
    public float respawnTimer = 0f;
    public float speedBoostTimer = 0f;

    public PlayerState(String playerId, String username, Vec2 spawnPos) {
        this.playerId = playerId;
        this.username = username;
        this.pos = spawnPos;
    }

    public PlayerDto toDto() {
        PlayerDto dto = new PlayerDto(
                playerId,
                username,
                pos,
                vel,
                facing,
                hp,
                moveSpeed,
                equippedWeaponType,
                equippedAmmo
        );
        dto.score = score;
        dto.isDead = isDead;
        dto.respawnTimer = respawnTimer;
        dto.speedBoostTimer = speedBoostTimer;
        return dto;
    }
}