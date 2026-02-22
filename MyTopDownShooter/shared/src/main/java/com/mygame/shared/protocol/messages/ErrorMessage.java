package com.mygame.shared.protocol.messages;

import com.mygame.shared.protocol.ProtocolConstants;
import com.mygame.shared.protocol.ServerMessage;

public final class ErrorMessage implements ServerMessage {
    public String code;
    public String message;

    public ErrorMessage() {}

    public ErrorMessage(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String type() {
        return ProtocolConstants.T_ERROR;
    }
}