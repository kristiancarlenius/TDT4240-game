package com.mygame.client.application.service;

import com.mygame.client.application.usecase.ApplySnapshotUseCase;
import com.mygame.client.domain.model.WorldState;
import com.mygame.client.net.NetClient;
import com.mygame.client.net.NetListener;
import com.mygame.shared.dto.GameSnapshotDto;
import com.mygame.shared.dto.MapDto;
import com.mygame.shared.protocol.messages.InputMessage;

/**
 * Facade that hides WebSocket / codec complexity from the rest of the client.
 * Owns the NetClient lifecycle and routes incoming server messages to use cases.
 */
public final class GameSessionService implements NetListener {

    private final WorldState           worldState;
    private final ApplySnapshotUseCase applySnapshot;
    private final Runnable             onDisconnected;
    private NetClient                  net;

    public GameSessionService(WorldState worldState,
                              ApplySnapshotUseCase applySnapshot,
                              Runnable onDisconnected) {
        this.worldState     = worldState;
        this.applySnapshot  = applySnapshot;
        this.onDisconnected = onDisconnected;
    }

    public void connect(String serverUrl, String username) {
        net = new NetClient(serverUrl, username, this);
        net.connectAsync();
    }

    public void disconnect() {
        if (net != null) net.close();
    }

    public boolean isConnected() {
        return net != null && net.isOpen();
    }

    public void sendInput(InputMessage input) {
        if (net != null) net.sendInput(input);
    }

    // ---- NetListener ----

    @Override
    public void onJoinAccepted(String playerId, MapDto map, GameSnapshotDto initialSnapshot) {
        worldState.setLocalPlayerId(playerId);
        worldState.setMap(map);
        worldState.applySnapshot(initialSnapshot);
        System.out.println("[SESSION] Joined as " + playerId);
    }

    @Override
    public void onSnapshot(GameSnapshotDto snapshot) {
        applySnapshot.execute(snapshot);
    }

    @Override
    public void onError(String code, String message) {
        System.err.println("[SESSION] Server error: " + code + " – " + message);
    }

    @Override
    public void onDisconnected(String reason) {
        System.err.println("[SESSION] Disconnected: " + reason);
        onDisconnected.run();
    }
}
