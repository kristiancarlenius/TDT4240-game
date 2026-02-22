package com.mygame.shared.dto;

public enum TileType {
    FLOOR,
    WALL,
    WINDOW,   // projectile passes, player doesn't
    TRAP      // walkable but damages on server
}