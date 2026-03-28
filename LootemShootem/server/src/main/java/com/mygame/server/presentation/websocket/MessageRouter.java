package com.mygame.server.presentation.websocket;

import com.mygame.server.application.usecase.HandleInputUseCase;
import com.mygame.server.application.usecase.HandleJoinUseCase;
import com.mygame.server.application.usecase.HandleLeaveUseCase;
import com.mygame.shared.protocol.MessageCodec;
import com.mygame.shared.protocol.messages.ErrorMessage;
import com.mygame.shared.protocol.messages.InputMessage;
import com.mygame.shared.protocol.messages.JoinAccepted;
import com.mygame.shared.protocol.messages.JoinRequest;
import org.java_websocket.WebSocket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes incoming WebSocket messages to the appropriate use case.
 * Keeps presentation logic (protocol, codec, connection tracking) here
 * and business logic in the use cases.
 *
 * Security notes:
 *  - Messages over MAX_MESSAGE_CHARS are rejected before parsing (DoS / parser-bomb guard).
 *  - Vec2 fields in InputMessage are validated for NaN/Infinity before being forwarded.
 *  - Username sanitisation lives in HandleJoinUseCase.
 */
public final class MessageRouter {

    /** ~4 KB – enough for any valid protocol message while blocking oversized payloads. */
    private static final int MAX_MESSAGE_CHARS = 4096;

    private final HandleJoinUseCase  joinUseCase;
    private final HandleLeaveUseCase leaveUseCase;
    private final HandleInputUseCase inputUseCase;
    private final MessageCodec       codec = new MessageCodec();

    // connection → playerId  (set after successful join)
    private final Map<WebSocket, String> playerByConn = new ConcurrentHashMap<>();

    public MessageRouter(HandleJoinUseCase joinUseCase,
                         HandleLeaveUseCase leaveUseCase,
                         HandleInputUseCase inputUseCase) {
        this.joinUseCase  = joinUseCase;
        this.leaveUseCase = leaveUseCase;
        this.inputUseCase = inputUseCase;
    }

    public void onConnected(WebSocket conn) {
        // Wait for JoinRequest — no action needed yet
    }

    public void onDisconnected(WebSocket conn) {
        String playerId = playerByConn.remove(conn);
        leaveUseCase.execute(playerId); // null-safe inside use case
    }

    public void onTextMessage(WebSocket conn, String json) {
        if (json == null || json.length() > MAX_MESSAGE_CHARS) {
            conn.send(codec.encode(new ErrorMessage("too_large", "Message exceeds size limit")));
            return;
        }
        try {
            Object msg = codec.decode(json);

            if (msg instanceof JoinRequest) {
                JoinRequest   join     = (JoinRequest) msg;
                JoinAccepted  accepted = joinUseCase.execute(join.username);
                if (accepted == null) {
                    conn.send(codec.encode(
                            new ErrorMessage("bad_request", "username is required")));
                    return;
                }
                playerByConn.put(conn, accepted.playerId);
                conn.send(codec.encode(accepted));
                return;
            }

            if (msg instanceof InputMessage) {
                String playerId = playerByConn.get(conn);
                if (playerId == null) {
                    conn.send(codec.encode(
                            new ErrorMessage("not_joined", "Send JoinRequest first")));
                    return;
                }
                InputMessage input = (InputMessage) msg;
                if (!isValidInput(input)) {
                    // Silently drop malformed input — never disconnect, just ignore.
                    return;
                }
                inputUseCase.execute(playerId, input);
                return;
            }

            conn.send(codec.encode(new ErrorMessage("unknown_message", "Unsupported message type")));

        } catch (Exception e) {
            System.err.println("[WS] DECODE FAILED  raw=" + json);
            e.printStackTrace();
            conn.send(codec.encode(new ErrorMessage("decode_error", "Invalid message format")));
        }
    }

    /** Called by tick loop — broadcasts the pre-encoded snapshot to everyone. */
    public void broadcastSnapshot(String snapshotJson) {
        // kept for backward-compat; wiring now goes through BroadcastSnapshotUseCase
    }

    // ---- Input validation ----

    private static boolean isValidInput(InputMessage m) {
        if (m == null || m.seq < 0) return false;
        if (!isValidVec2(m.move)) return false;
        if (!isValidVec2(m.aim))  return false;
        return true;
    }

    private static boolean isValidVec2(com.mygame.shared.util.Vec2 v) {
        if (v == null) return true; // null means "no input", handled downstream
        return !Float.isNaN(v.x) && !Float.isInfinite(v.x)
            && !Float.isNaN(v.y) && !Float.isInfinite(v.y);
    }
}
