package com.mygame.server;

import com.mygame.server.application.service.MatchService;
import com.mygame.server.application.service.TickService;
import com.mygame.server.presentation.websocket.GameWebSocketServer;

public final class ServerMain {

    public static void main(String[] args) throws Exception {
        runCodecSelfTest();
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

    private static void runCodecSelfTest() {
        try {
            var codec = new com.mygame.shared.protocol.MessageCodec();

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