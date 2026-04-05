package com.mygame.server.data.map.procedural;

import com.mygame.server.application.service.map.ProceduralMapController;
import com.mygame.server.application.service.map.procedural.*;
import com.mygame.server.domain.model.ServerGameState;
import com.mygame.server.domain.model.proc.GeneratedMapBlueprint;
import com.mygame.server.domain.model.proc.MapGenerationSpec;
import com.mygame.server.domain.ports.MapProviderPort;

/**
 * Data layer provider for procedurally-generated maps.
 * Encapsulates the composition of all procedural map generation components and provides
 * a façade for creating new game states from generated map blueprints.
 * Uses seed-based determinism to ensure reproducible maps for the same mapId.
 */
public final class ProceduralMapProvider implements MapProviderPort {

    private final ProceduralMapController controller;
    private final long baseSeed;

    /**
     * Creates a procedural map provider with the given base seed.
     * Initializes all map generation components: skeleton graph builder, border state assigner,
     * room populator with template catalog and room generator.
     *
     * @param baseSeed base seed for deterministic map generation
     */
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

    /**
     * Provides a procedurally-generated game state for the given map ID.
     * Combines the base seed with the map ID hash to create a unique, reproducible seed.
     * Generates the map blueprint and converts it to a ServerGameState.
     *
     * @param mapId unique identifier for the map
     * @return game state with generated map tiles and chest spawn points
     */
    @Override
    public ServerGameState provide(String mapId) {
        long seed = baseSeed ^ mapId.hashCode();
        GeneratedMapBlueprint blueprint = controller.generate(mapId, seed);
        System.out.println("[MAP] Generated procedural map '" + mapId + "' ("
                + blueprint.worldWidth + "x" + blueprint.worldHeight + ") seed=" + seed);
        return ServerGameState.fromTiles(
            mapId,
            blueprint.worldWidth,
            blueprint.worldHeight,
            blueprint.tiles,
            blueprint.chestSpawnPoints);
    }
}
