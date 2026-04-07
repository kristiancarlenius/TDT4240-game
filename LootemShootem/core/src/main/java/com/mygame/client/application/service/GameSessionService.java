package com.mygame.client.application.service;

import com.mygame.client.application.usecase.ApplySnapshotUseCase;
import com.mygame.client.domain.model.WorldState;
import com.mygame.client.net.NetClient;
import com.mygame.client.net.NetListener;
import com.mygame.shared.dto.GameSnapshotDto;
import com.mygame.shared.dto.MapDto;
import com.mygame.shared.protocol.messages.InputMessage;

public final class GameSessionService implements NetListener {

    private final WorldState           worldState;
    private final ApplySnapshotUseCase applySnapshot;
    private final Runnable             onDisconnected;
    private NetClient                  net;
    private volatile boolean           joined = false;

    public GameSessionService(WorldState worldState,
                              ApplySnapshotUseCase applySnapshot,
                              Runnable onDisconnected) {
        this.worldState     = worldState;
        this.applySnapshot  = applySnapshot;
        this.onDisconnected = onDisconnected;
    }

    public void connect(String serverUrl, String username, int skinId) {
        net = new NetClient(serverUrl, username, skinId, this);
        net.connectAsync();
    }

    public void disconnect() {
        if (net != null) net.close();
    }

    public boolean isConnected() {
        return net != null && net.isOpen();
    }

    public boolean isJoined() {
        return joined && net != null && net.isOpen();
    }

    public void sendInput(InputMessage input) {
        if (net != null) net.sendInput(input);
    }

    @Override
    public void onJoinAccepted(String playerId, MapDto map, GameSnapshotDto initialSnapshot) {
        joined = true;
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
        joined = false;
        System.err.println("[SESSION] Disconnected: " + reason);
        onDisconnected.run();
    }
}
