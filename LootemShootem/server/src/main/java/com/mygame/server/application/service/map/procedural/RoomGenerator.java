package com.mygame.server.application.service.map.procedural;

import com.mygame.server.domain.model.proc.RoomGenerationContext;
import com.mygame.server.domain.model.proc.RoomSelection;
import com.mygame.server.domain.model.proc.RoomTemplate;

import java.util.List;

public interface RoomGenerator {
    RoomSelection select(RoomGenerationContext context, List<RoomTemplate> candidates);
}
