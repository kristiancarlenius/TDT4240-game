package com.mygame.shared.dto;

public final class MapDto {
    public String mapId;
    public int width;
    public int height;

    /**
     * Row-major tiles: index = y * width + x
     */
    public TileType[] tiles;

    public MapDto() {}

    public MapDto(String mapId, int width, int height, TileType[] tiles) {
        this.mapId = mapId;
        this.width = width;
        this.height = height;
        this.tiles = tiles;
    }
}