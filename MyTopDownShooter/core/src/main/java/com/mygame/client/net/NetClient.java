package com.mygame.client.net;

import com.mygame.shared.dto.GameSnapshotDto;
import com.mygame.shared.dto.MapDto;
import com.mygame.shared.protocol.MessageCodec;
import com.mygame.shared.protocol.messages.ErrorMessage;
import com.mygame.shared.protocol.messages.InputMessage;
import com.mygame.shared.protocol.messages.JoinAccepted;
import com.mygame.shared.protocol.messages.JoinRequest;
import com.mygame.shared.protocol.messages.SnapshotMessage;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;

public final class NetClient {

    private final String serverUrl;
    private final String username;
    private final NetListener listener;

    private final MessageCodec codec = new MessageCodec();
    private WebSocketClient ws;

    public NetClient(String serverUrl, String username, NetListener listener) {
        this.serverUrl = serverUrl;
        this.username = username;
        this.listener = listener;
    }

    public void connectAsync() {
        try {
            ws = new WebSocketClient(new URI(serverUrl)) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    // Send JoinRequest immediately
                    send(codec.encode(new JoinRequest(username)));
                }

                @Override
                public void onMessage(String message) {
                    Object msg = codec.decode(message);

                    if (msg instanceof JoinAccepted) {
                        JoinAccepted ja = (JoinAccepted) msg;
                        String playerId = ja.playerId;
                        MapDto map = ja.map;
                        GameSnapshotDto snap = ja.initialSnapshot;
                        listener.onJoinAccepted(playerId, map, snap);
                        return;
                    }

                    if (msg instanceof SnapshotMessage) {
                        SnapshotMessage sm = (SnapshotMessage) msg;
                        listener.onSnapshot(sm.snapshot);
                        return;
                    }

                    if (msg instanceof ErrorMessage) {
                        ErrorMessage em = (ErrorMessage) msg;
                        listener.onError(em.code, em.message);
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    listener.onDisconnected(reason);
                }

                @Override
                public void onError(Exception ex) {
                    listener.onDisconnected("ws error: " + ex);
                }
            };

            ws.connect(); // non-blocking
        } catch (Exception e) {
            listener.onDisconnected("connect failed: " + e);
        }
    }

    public boolean isOpen() {
        return ws != null && ws.isOpen();
    }

    public void sendInput(InputMessage input) {
        if (ws == null || !ws.isOpen()) return;
        String json = codec.encode(input);
        System.out.println("[CLIENT] -> " + json);
        ws.send(json);
    }

    public void close() {
        if (ws != null) ws.close();
    }
}