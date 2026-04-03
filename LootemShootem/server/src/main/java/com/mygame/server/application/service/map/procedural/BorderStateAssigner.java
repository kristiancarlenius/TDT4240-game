package com.mygame.server.application.service.map.procedural;

import com.mygame.server.domain.model.proc.MapGenerationSpec;
import com.mygame.server.domain.model.proc.MapGraph;

import java.util.Random;

public interface BorderStateAssigner {
    void assign(MapGraph graph, Random rng, MapGenerationSpec spec);
}
