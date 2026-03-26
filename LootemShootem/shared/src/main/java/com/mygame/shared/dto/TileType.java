package com.mygame.shared.dto;

public enum TileType {
    FLOOR,
    WALL,
    WINDOW,   // projectiles pass through, players cannot
    TRAP,     // walkable; damages over time
    COBWEB    // walkable; reduces move speed to 1/3
}
