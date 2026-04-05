package com.mygame.server.domain.model.proc;

public final class RoomNode {

    public final String id;
    public final RoomKind kind;

    // Playable-space bounds (0-based, inclusive).
    public final int minX;
    public final int minY;
    public final int maxX;
    public final int maxY;

    // Grid slot metadata for debug/constraints.
    public final int slotCol;
    public final int slotRow;

    public String templateId;
    public RoomTransform transform = RoomTransform.ROT_0;

    public RoomNode(
            String id,
            RoomKind kind,
            int minX,
            int minY,
            int maxX,
            int maxY,
            int slotCol,
            int slotRow) {
        this.id = id;
        this.kind = kind;
        this.minX = minX;
        this.minY = minY;
        this.maxX = maxX;
        this.maxY = maxY;
        this.slotCol = slotCol;
        this.slotRow = slotRow;
    }

    public int width() {
        return maxX - minX + 1;
    }

    public int height() {
        return maxY - minY + 1;
    }
}
