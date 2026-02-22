package com.mygame.server.presentation.websocket;

import com.mygame.server.application.service.MatchService;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;

public final class GameWebSocketServer extends WebSocketServer {

    private final MessageRouter router;

    public GameWebSocketServer(int port, MatchService matchService) {
        super(new InetSocketAddress(port));
        this.router = new MessageRouter(matchService, this);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        // We accept any path; client can still use /ws.
        System.out.println("[WS] open " + conn.getRemoteSocketAddress());
        router.onConnected(conn);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        System.out.println("[WS] close " + conn.getRemoteSocketAddress() + " reason=" + reason);
        router.onDisconnected(conn);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        router.onTextMessage(conn, message);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("[WS] error " + (conn != null ? conn.getRemoteSocketAddress() : "(no-conn)") + " " + ex);
    }

    @Override
    public void onStart() {
        System.out.println("[WS] started");
        setConnectionLostTimeout(15);
    }

    /** Broadcast to all current sockets. */
    public void broadcastText(String text) {
        broadcast(text);
    }
}