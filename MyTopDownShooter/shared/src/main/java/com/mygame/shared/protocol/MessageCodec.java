package com.mygame.shared.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mygame.shared.protocol.messages.*;

import java.util.HashMap;
import java.util.Map;

public final class MessageCodec {

    private final ObjectMapper mapper = new ObjectMapper();

    // map type -> class
    private final Map<String, Class<?>> typeToClass = new HashMap<>();

    public MessageCodec() {
        // Client messages
        typeToClass.put(ProtocolConstants.T_JOIN_REQUEST, JoinRequest.class);
        typeToClass.put(ProtocolConstants.T_INPUT, InputMessage.class);

        // Server messages
        typeToClass.put(ProtocolConstants.T_JOIN_ACCEPTED, JoinAccepted.class);
        typeToClass.put(ProtocolConstants.T_SNAPSHOT, SnapshotMessage.class);
        typeToClass.put(ProtocolConstants.T_ERROR, ErrorMessage.class);
    }

    /** Encode any message (client or server) into JSON string. */
    public String encode(Object message) {
        String type;
        if (message instanceof ClientMessage) type = ((ClientMessage) message).type();
        else if (message instanceof ServerMessage) type = ((ServerMessage) message).type();
        else throw new IllegalArgumentException("Unknown message type: " + message.getClass());

        Envelope env = new Envelope(type, message);
        try {
            return mapper.writeValueAsString(env);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to encode message: " + type, e);
        }
    }

    /**
     * Decode JSON string into a concrete message object.
     * Returns Object; caller can instanceof check or switch on type().
     */
    public Object decode(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            String type = root.get("type").asText();
            JsonNode payload = root.get("payload");

            Class<?> clazz = typeToClass.get(type);
            if (clazz == null) {
                throw new IllegalArgumentException("Unknown message type: " + type);
            }
            return mapper.treeToValue(payload, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode message: " + json, e);
        }
    }
}