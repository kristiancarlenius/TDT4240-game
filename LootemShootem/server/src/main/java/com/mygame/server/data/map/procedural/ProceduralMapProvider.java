package com.mygame.server.data.map.procedural;

import com.mygame.server.application.service.map.ProceduralMapController;
import com.mygame.server.application.service.map.procedural.*;
import com.mygame.server.domain.model.ServerGameState;
import com.mygame.server.domain.model.proc.GeneratedMapBlueprint;
import com.mygame.server.domain.model.proc.MapGenerationSpec;
import com.mygame.server.domain.ports.MapProviderPort;

public final class ProceduralMapProvider implements MapProviderPort {

    private final ProceduralMapController controller;
    private final long baseSeed;

    public ProceduralMapProvider(long baseSeed) {
        MapGenerationSpec spec = MapGenerationSpec.defaultSpec();

        JsonRoomParser parser = new JsonRoomParser();
        JsonRoomTemplateCatalog catalog = new JsonRoomTemplateCatalog(parser);
        RoomGenerator roomGenerator = new RandomRoomGenerator();

        this.controller = new ProceduralMapController(
                spec,
                new GridSkeletonGraphBuilder(),
                new WeightedBorderStateAssigner(new GraphConnectivityValidator()),
                new RoomPopulator(catalog, roomGenerator));
        this.baseSeed = baseSeed;
    }

    @Override
    public ServerGameState provide(String mapId) {
        long seed = baseSeed ^ mapId.hashCode();
        GeneratedMapBlueprint blueprint = controller.generate(mapId, seed);
        System.out.println("[MAP] Generated procedural map '" + mapId + "' ("
                + blueprint.worldWidth + "x" + blueprint.worldHeight + ") seed=" + seed);
        return ServerGameState.fromTiles(mapId, blueprint.worldWidth, blueprint.worldHeight, blueprint.tiles);
    }
}
