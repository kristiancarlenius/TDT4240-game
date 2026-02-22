package com.mygame.server.domain.model;

import com.mygame.shared.dto.ProjectileDto;
import com.mygame.shared.util.Vec2;

public final class ProjectileState {
    public final String projectileId;
    public final String ownerPlayerId;

    public Vec2 pos;
    public Vec2 vel;

    public float damage;
    public float radius;
    public float ttlSeconds;

    public ProjectileState(String projectileId, String ownerPlayerId, Vec2 pos, Vec2 vel,
                           float damage, float radius, float ttlSeconds) {
        this.projectileId = projectileId;
        this.ownerPlayerId = ownerPlayerId;
        this.pos = pos;
        this.vel = vel;
        this.damage = damage;
        this.radius = radius;
        this.ttlSeconds = ttlSeconds;
    }

    public ProjectileDto toDto() {
        return new ProjectileDto(projectileId, ownerPlayerId, pos, vel, damage, radius, ttlSeconds);
    }
}