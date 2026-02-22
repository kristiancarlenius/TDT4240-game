package com.mygame.shared.dto;

public final class GameSnapshotDto {
    public long tick;
    public PlayerDto[] players;
    public ProjectileDto[] projectiles;
    public PickupDto[] pickups;

    public GameSnapshotDto() {}

    public GameSnapshotDto(long tick, PlayerDto[] players, ProjectileDto[] projectiles, PickupDto[] pickups) {
        this.tick = tick;
        this.players = players;
        this.projectiles = projectiles;
        this.pickups = pickups;
    }
}