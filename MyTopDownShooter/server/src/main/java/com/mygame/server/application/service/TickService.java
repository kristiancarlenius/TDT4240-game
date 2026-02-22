package com.mygame.server.application.service;

import com.mygame.server.presentation.websocket.GameWebSocketServer;
import com.mygame.shared.protocol.MessageCodec;
import com.mygame.shared.protocol.messages.SnapshotMessage;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Fixed-timestep tick loop (authoritative server).
 */
public final class TickService {

    private final MatchService matchService;
    private final GameWebSocketServer wsServer;
    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
    private final MessageCodec codec = new MessageCodec();

    // MVP settings
    private static final int TICK_HZ = 30;
    private static final float DT = 1.0f / TICK_HZ;

    public TickService(MatchService matchService, GameWebSocketServer wsServer) {
        this.matchService = matchService;
        this.wsServer = wsServer;
    }

    public void start() {
        long periodMs = Math.round(1000.0 / TICK_HZ);
        exec.scheduleAtFixedRate(() -> {
            try {
                matchService.tick(DT);
                SnapshotMessage msg = new SnapshotMessage(matchService.buildSnapshot());
                wsServer.broadcastText(codec.encode(msg));
            } catch (Exception e) {
                System.err.println("[TICK] error: " + e);
            }
        }, 0, periodMs, TimeUnit.MILLISECONDS);

        System.out.println("[SERVER] Tick loop started @ " + TICK_HZ + " Hz");
    }
}