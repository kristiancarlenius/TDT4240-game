package com.mygame.shared.protocol.messages;

import com.mygame.shared.dto.GameSnapshotDto;
import com.mygame.shared.protocol.ProtocolConstants;
import com.mygame.shared.protocol.ServerMessage;

public final class SnapshotMessage implements ServerMessage {
    public GameSnapshotDto snapshot;

    public SnapshotMessage() {}

    public SnapshotMessage(GameSnapshotDto snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public String type() {
        return ProtocolConstants.T_SNAPSHOT;
    }
}