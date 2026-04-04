package com.mygame.server.application.service.map.procedural;

import com.mygame.server.domain.model.proc.MapGenerationSpec;
import com.mygame.server.domain.model.proc.MapGraph;

/**
 * Strategy interface for building a dungeon room graph from a map generation specification.
 * The graph defines rooms (nodes) and boundaries between them (edges).
 */
public interface SkeletonGraphBuilder {
    /**
     * Builds a room graph from the map specification.
     * Creates room nodes and border edges representing potential doors/walls.
     *
     * @param spec map generation specification
     * @return map graph with rooms and borders
     */
    MapGraph build(MapGenerationSpec spec);
}
