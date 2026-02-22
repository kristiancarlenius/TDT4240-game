package com.mygame.client.net;

import com.mygame.shared.dto.GameSnapshotDto;
import com.mygame.shared.dto.MapDto;

public interface NetListener {
    void onJoinAccepted(String playerId, MapDto map, GameSnapshotDto initialSnapshot);
    void onSnapshot(GameSnapshotDto snapshot);
    void onError(String code, String message);
    void onDisconnected(String reason);
}