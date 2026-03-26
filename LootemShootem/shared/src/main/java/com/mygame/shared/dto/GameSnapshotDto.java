package com.mygame.shared.dto;

public final class GameSnapshotDto {
    public long tick;
    public PlayerDto[] players;
    public ProjectileDto[] projectiles;
    public PickupDto[] pickups;
    public ChestDto[] chests;
    /** Kill events that occurred this tick, e.g. "Alice killed Bob" or "Bob died in a trap". */
    public String[] killFeed;

    public GameSnapshotDto() {}

    public GameSnapshotDto(long tick, PlayerDto[] players, ProjectileDto[] projectiles,
                           PickupDto[] pickups, ChestDto[] chests, String[] killFeed) {
        this.tick = tick;
        this.players = players;
        this.projectiles = projectiles;
        this.pickups = pickups;
        this.chests = chests;
        this.killFeed = killFeed;
    }
}