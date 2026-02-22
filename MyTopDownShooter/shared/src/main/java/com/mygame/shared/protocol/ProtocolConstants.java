package com.mygame.shared.protocol;

public final class ProtocolConstants {
    private ProtocolConstants() {}

    public static final int PROTOCOL_VERSION = 1;

    public static final String T_JOIN_REQUEST = "join_request";
    public static final String T_JOIN_ACCEPTED = "join_accepted";
    public static final String T_INPUT = "input";
    public static final String T_SNAPSHOT = "snapshot";
    public static final String T_PING = "ping";
    public static final String T_PONG = "pong";
    public static final String T_ERROR = "error";
}