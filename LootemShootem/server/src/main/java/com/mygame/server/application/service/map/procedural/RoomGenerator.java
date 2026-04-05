package com.mygame.server.application.service.map.procedural;

import com.mygame.server.domain.model.proc.RoomGenerationContext;
import com.mygame.server.domain.model.proc.RoomSelection;
import com.mygame.server.domain.model.proc.RoomTemplate;

import java.util.List;

/**
 * Strategy interface for selecting a room template and transformation for a given room slot.
 */
public interface RoomGenerator {

    /**
     * Selects a template and transformation for the given room.
     *
     * @param context generation context with room dimensions, borders, and RNG
     * @param candidates available room templates of the appropriate kind
     * @return selected template ID and transformation
     */
    RoomSelection select(RoomGenerationContext context, List<RoomTemplate> candidates);
}
