package com.mygame.shared.protocol.messages;

import com.mygame.shared.protocol.ClientMessage;
import com.mygame.shared.protocol.ProtocolConstants;

public final class JoinRequest implements ClientMessage {
    public int protocolVersion = ProtocolConstants.PROTOCOL_VERSION;
    public String username;

    public JoinRequest() {}

    public JoinRequest(String username) {
        this.username = username;
    }

    @Override
    public String type() {
        return ProtocolConstants.T_JOIN_REQUEST;
    }
}