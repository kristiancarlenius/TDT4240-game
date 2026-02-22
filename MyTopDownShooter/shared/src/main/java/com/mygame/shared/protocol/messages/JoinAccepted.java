package com.mygame.shared.protocol.messages;

import com.mygame.shared.dto.GameSnapshotDto;
import com.mygame.shared.dto.MapDto;
import com.mygame.shared.protocol.ProtocolConstants;
import com.mygame.shared.protocol.ServerMessage;

public final class JoinAccepted implements ServerMessage {
    public String playerId;
    public MapDto map;
    public GameSnapshotDto initialSnapshot;

    public JoinAccepted() {}

    public JoinAccepted(String playerId, MapDto map, GameSnapshotDto initialSnapshot) {
        this.playerId = playerId;
        this.map = map;
        this.initialSnapshot = initialSnapshot;
    }

    @Override
    public String type() {
        return ProtocolConstants.T_JOIN_ACCEPTED;
    }
}