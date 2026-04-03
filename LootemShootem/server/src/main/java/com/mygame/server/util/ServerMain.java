package com.mygame.server;

import com.mygame.server.application.service.MatchService;
import com.mygame.server.application.service.TickService;
import com.mygame.server.application.usecase.*;
import com.mygame.server.config.ServerConfig;
import com.mygame.server.data.map.MapProvider;
import com.mygame.server.data.map.procedural.ProceduralMapProvider;
import com.mygame.server.domain.ports.MapProviderPort;
import com.mygame.server.presentation.websocket.GameWebSocketServer;
import com.mygame.server.util.RateLimiter;

public final class ServerMain {

    public static void main(String[] args) throws Exception {
        runCodecSelfTest();

        ServerConfig config = ServerConfig.load();

        // Domain
        MapProviderPort mapProvider = "procedural".equals(config.mapMode)
            ? new ProceduralMapProvider(config.mapSeed)
            : new MapProvider();
        MatchService matchService = new MatchService(mapProvider, config.mapId);

        // Rate limiter: allow up to 60 inputs/second per player
        RateLimiter rateLimiter = new RateLimiter(60);

        // Use cases
        HandleJoinUseCase  joinUseCase  = new HandleJoinUseCase(matchService, config.maxPlayers);
        HandleLeaveUseCase leaveUseCase = new HandleLeaveUseCase(matchService, rateLimiter);
        HandleInputUseCase inputUseCase = new HandleInputUseCase(matchService, rateLimiter);

        // Presentation
        GameWebSocketServer wsServer = new GameWebSocketServer(
                config.port, joinUseCase, leaveUseCase, inputUseCase);

        BroadcastSnapshotUseCase broadcastUseCase =
                new BroadcastSnapshotUseCase(matchService, wsServer);

        wsServer.start();
        System.out.println("[SERVER] WebSocket listening on ws://localhost:"
                + config.port + "/ws");

        new TickService(matchService, broadcastUseCase, config.tickHz).start();

        Thread.currentThread().join();
    }

    private static void runCodecSelfTest() {
        try {
            var codec  = new com.mygame.shared.protocol.MessageCodec();
            String sample = "{\"type\":\"input\",\"payload\":{\"seq\":1,"
                    + "\"move\":{\"x\":1.0,\"y\":0.0},"
                    + "\"aim\":{\"x\":0.0,\"y\":1.0},"
                    + "\"shoot\":false,"
                    + "\"switchWeapon\":false}}";
            Object decoded = codec.decode(sample);
            System.out.println("[SELFTEST] decode OK: " + decoded.getClass().getName());
        } catch (Exception e) {
            System.err.println("[SELFTEST] decode FAILED");
            e.printStackTrace();
        }
    }
}
