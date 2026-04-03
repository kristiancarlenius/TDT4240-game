package com.mygame.server.domain.model.proc;

public final class BorderEdge {

    public final String id;
    public final String nodeA;
    public final String nodeB;
    public final BorderOrientation orientation;

    // Playable-space border geometry (inclusive span).
    public final int lineCoord;
    public final int spanStart;
    public final int spanEnd;

    public BorderState state = BorderState.WALL;
    public String variantId = "default";

    public BorderEdge(
            String id,
            String nodeA,
            String nodeB,
            BorderOrientation orientation,
            int lineCoord,
            int spanStart,
            int spanEnd) {
        this.id = id;
        this.nodeA = nodeA;
        this.nodeB = nodeB;
        this.orientation = orientation;
        this.lineCoord = lineCoord;
        this.spanStart = spanStart;
        this.spanEnd = spanEnd;
    }

    public boolean touches(String roomId) {
        return nodeA.equals(roomId) || nodeB.equals(roomId);
    }

    public String other(String roomId) {
        return nodeA.equals(roomId) ? nodeB : nodeA;
    }
}
