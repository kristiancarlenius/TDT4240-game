package com.mygame.client.net;

import com.mygame.client.data.network.NetMapper;
import com.mygame.client.data.network.WebSocketTransport;
import com.mygame.shared.protocol.messages.ErrorMessage;
import com.mygame.shared.protocol.messages.InputMessage;
import com.mygame.shared.protocol.messages.JoinAccepted;
import com.mygame.shared.protocol.messages.JoinRequest;
import com.mygame.shared.protocol.messages.SnapshotMessage;

public final class NetClient {

    private final String             serverUrl;
    private final String             username;
    private final NetListener        listener;
    private final WebSocketTransport transport = new WebSocketTransport();
    private final NetMapper          mapper    = new NetMapper();

    public NetClient(String serverUrl, String username, NetListener listener) {
        this.serverUrl = serverUrl;
        this.username  = username;
        this.listener  = listener;
    }

    public void connectAsync() {
        transport.connect(serverUrl, new WebSocketTransport.Listener() {
            @Override
            public void onOpen() {
                transport.send(mapper.encode(new JoinRequest(username)));
            }

            @Override
            public void onMessage(String text) {
                Object msg = mapper.decode(text);
                if (msg instanceof JoinAccepted) {
                    JoinAccepted ja = (JoinAccepted) msg;
                    listener.onJoinAccepted(ja.playerId, ja.map, ja.initialSnapshot);
                } else if (msg instanceof SnapshotMessage) {
                    listener.onSnapshot(((SnapshotMessage) msg).snapshot);
                } else if (msg instanceof ErrorMessage) {
                    ErrorMessage em = (ErrorMessage) msg;
                    listener.onError(em.code, em.message);
                }
            }

            @Override
            public void onClose(String reason) {
                listener.onDisconnected(reason);
            }

            @Override
            public void onError(Exception ex) {
                listener.onDisconnected("ws error: " + ex);
            }
        });
    }

    public boolean isOpen()              { return transport.isOpen(); }
    public void    sendInput(InputMessage input) { transport.send(mapper.encode(input)); }
    public void    close()               { transport.close(); }
}
