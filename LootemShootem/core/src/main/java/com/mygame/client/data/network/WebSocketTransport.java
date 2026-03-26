package com.mygame.client.data.network;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;

public final class WebSocketTransport {

    public interface Listener {
        void onOpen();
        void onMessage(String text);
        void onClose(String reason);
        void onError(Exception ex);
    }

    private WebSocketClient ws;

    public void connect(String url, Listener listener) {
        try {
            ws = new WebSocketClient(new URI(url)) {
                @Override public void onOpen(ServerHandshake h)               { listener.onOpen(); }
                @Override public void onMessage(String msg)                   { listener.onMessage(msg); }
                @Override public void onClose(int c, String r, boolean remote){ listener.onClose(r); }
                @Override public void onError(Exception ex)                   { listener.onError(ex); }
            };
            ws.connect();
        } catch (Exception e) {
            listener.onClose("connect failed: " + e);
        }
    }

    public void send(String text) {
        if (ws != null && ws.isOpen()) ws.send(text);
    }

    public boolean isOpen() {
        return ws != null && ws.isOpen();
    }

    public void close() {
        if (ws != null) ws.close();
    }
}
