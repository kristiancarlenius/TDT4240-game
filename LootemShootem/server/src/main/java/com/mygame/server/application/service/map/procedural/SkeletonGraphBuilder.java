package com.mygame.server.application.service.map.procedural;

import com.mygame.server.domain.model.proc.MapGenerationSpec;
import com.mygame.server.domain.model.proc.MapGraph;

public interface SkeletonGraphBuilder {
    MapGraph build(MapGenerationSpec spec);
}
