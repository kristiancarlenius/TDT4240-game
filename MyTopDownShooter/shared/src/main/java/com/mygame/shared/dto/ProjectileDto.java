package com.mygame.shared.dto;

import com.mygame.shared.util.Vec2;

public final class ProjectileDto {
    public String projectileId;
    public String ownerPlayerId;

    public Vec2 pos;
    public Vec2 vel;

    public float damage;
    public float radius;
    public float ttlSeconds;

    public ProjectileDto() {}

    public ProjectileDto(String projectileId, String ownerPlayerId, Vec2 pos, Vec2 vel,
                         float damage, float radius, float ttlSeconds) {
        this.projectileId = projectileId;
        this.ownerPlayerId = ownerPlayerId;
        this.pos = pos;
        this.vel = vel;
        this.damage = damage;
        this.radius = radius;
        this.ttlSeconds = ttlSeconds;
    }
}