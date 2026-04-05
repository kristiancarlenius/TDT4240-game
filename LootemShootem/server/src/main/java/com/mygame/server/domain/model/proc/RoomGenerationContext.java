package com.mygame.server.domain.model.proc;

import java.util.List;
import java.util.Random;

public final class RoomGenerationContext {

    public final RoomNode room;
    public final List<BorderEdge> incidentEdges;
    public final Random rng;

    public RoomGenerationContext(RoomNode room, List<BorderEdge> incidentEdges, Random rng) {
        this.room = room;
        this.incidentEdges = incidentEdges;
        this.rng = rng;
    }
}
