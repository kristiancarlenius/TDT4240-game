package com.mygame.server.presentation.websocket;

import com.mygame.server.application.service.MatchService;
import com.mygame.shared.protocol.MessageCodec;
import com.mygame.shared.protocol.messages.ErrorMessage;
import com.mygame.shared.protocol.messages.InputMessage;
import com.mygame.shared.protocol.messages.JoinRequest;
import org.java_websocket.WebSocket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MessageRouter {

    private final MatchService matchService;
    private final GameWebSocketServer server;
    private final MessageCodec codec = new MessageCodec();

    // socket -> playerId (after join)
    private final Map<WebSocket, String> playerByConn = new ConcurrentHashMap<>();

    public MessageRouter(MatchService matchService, GameWebSocketServer server) {
        this.matchService = matchService;
        this.server = server;
    }

    public void onConnected(WebSocket conn) {
        // wait for JoinRequest
    }

    public void onDisconnected(WebSocket conn) {
        String playerId = playerByConn.remove(conn);
        if (playerId != null) {
            matchService.removePlayer(playerId);
        }
    }

    public void onTextMessage(WebSocket conn, String json) {
        try {
            Object msg = codec.decode(json);

            if (msg instanceof JoinRequest) {
                JoinRequest join = (JoinRequest) msg;

                if (join.username == null || join.username.isBlank()) {
                    conn.send(codec.encode(new ErrorMessage("bad_request", "username is required")));
                    return;
                }

                String playerId = matchService.addPlayer(join.username);
                playerByConn.put(conn, playerId);

                // Send JoinAccepted (includes map + initial snapshot)
                conn.send(codec.encode(matchService.buildJoinAccepted(playerId)));
                return;
            }

            if (msg instanceof InputMessage) {
                String playerId = playerByConn.get(conn);
                if (playerId == null) {
                    conn.send(codec.encode(new ErrorMessage("not_joined", "Send JoinRequest first")));
                    return;
                }

                matchService.submitInput(playerId, (InputMessage) msg);
                return;
            }

            conn.send(codec.encode(new ErrorMessage("unknown_message", "Unsupported message")));
        } catch (Exception e) {
            System.err.println("\n[WS] DECODE FAILED");
            System.err.println("[WS] raw: " + json);
            e.printStackTrace();
            conn.send(codec.encode(new ErrorMessage("decode_error", "Invalid message format")));
        }
    }

    /**
     * Called by tick loop: send snapshot to everyone (simple MVP broadcast).
     */
    public void broadcastSnapshot(String snapshotJson) {
        server.broadcastText(snapshotJson);
    }
}