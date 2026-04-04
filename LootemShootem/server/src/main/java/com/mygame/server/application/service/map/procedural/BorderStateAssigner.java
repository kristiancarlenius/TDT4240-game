package com.mygame.server.application.service.map.procedural;

import com.mygame.server.domain.model.proc.MapGenerationSpec;
import com.mygame.server.domain.model.proc.MapGraph;

import java.util.Random;

/**
 * Strategy interface for assigning states (WALL, DOOR, WINDOW, NONE) to room boundaries.
 * Determines which edges will be open passages, doors, visual barriers, or walls.
 */
public interface BorderStateAssigner {
    /**
     * Assigns states to all edges in the graph.
     * Must ensure the graph remains traversable (at least one path connects all rooms).
     *
     * @param graph room graph with unassigned border edges
     * @param rng random number generator (seeded)
     * @param spec map generation specification with weighting parameters
     */
    void assign(MapGraph graph, Random rng, MapGenerationSpec spec);
}
