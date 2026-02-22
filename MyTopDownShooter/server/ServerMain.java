package com.mygame.server;

import com.mygame.server.application.service.MatchService;
import com.mygame.server.application.service.TickService;
import com.mygame.server.presentation.websocket.GameWebSocketServer;

public final class ServerMain {

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("SERVER_PORT", "8080"));

        MatchService matchService = new MatchService();
        GameWebSocketServer wsServer = new GameWebSocketServer(port, matchService);

        wsServer.start();
        System.out.println("[SERVER] WebSocket listening on ws://localhost:" + port + "/ws");

        TickService tickService = new TickService(matchService, wsServer);
        tickService.start();

        // keep main thread alive
        Thread.currentThread().join();
    }
}