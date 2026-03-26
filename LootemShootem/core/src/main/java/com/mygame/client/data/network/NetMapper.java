package com.mygame.client.data.network;

import com.mygame.shared.protocol.MessageCodec;

public final class NetMapper {

    private final MessageCodec codec = new MessageCodec();

    public String encode(Object msg) {
        return codec.encode(msg);
    }

    public Object decode(String json) {
        return codec.decode(json);
    }
}
