package com.mygame.shared.protocol.messages;

import com.mygame.shared.protocol.ClientMessage;
import com.mygame.shared.protocol.ProtocolConstants;

public final class JoinRequest implements ClientMessage {
    public int protocolVersion = ProtocolConstants.PROTOCOL_VERSION;
    public String username;

    /** Skin index (0–3) chosen by the player. */
    public int skinId;

    public JoinRequest() {}

    public JoinRequest(String username) {
        this.username = username;
    }

    public JoinRequest(String username, int skinId) {
        this.username = username;
        this.skinId   = skinId;
    }

    @Override
    public String type() {
        return ProtocolConstants.T_JOIN_REQUEST;
    }
}