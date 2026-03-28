package com.mygame.server.application.usecase;

import com.mygame.server.application.service.MatchService;
import com.mygame.shared.protocol.messages.JoinAccepted;

/**
 * Encapsulates the join flow:
 *   validate → sanitise username → enforce max-player cap → add player → return accepted payload.
 *
 * Security: usernames are stripped to [a-zA-Z0-9_\- ] so they can be safely
 * embedded in kill-feed strings and displayed in any UI without injection risk.
 */
public final class HandleJoinUseCase {

    /** Only these characters are allowed in a username. */
    private static final String ALLOWED_CHARS_REGEX = "[^a-zA-Z0-9_\\- ]";
    private static final int    MAX_LEN             = 20;
    private static final int    MIN_LEN             = 1;

    private final MatchService matchService;
    private final int          maxPlayers;

    public HandleJoinUseCase(MatchService matchService, int maxPlayers) {
        this.matchService = matchService;
        this.maxPlayers   = maxPlayers;
    }

    /**
     * @return the {@link JoinAccepted} payload to send back, or {@code null} if
     *         the server is full or the username is invalid after sanitising.
     */
    public JoinAccepted execute(String rawUsername) {
        if (matchService.getPlayerCount() >= maxPlayers) {
            System.out.println("[JOIN] Rejected (server full)");
            return null;
        }

        String clean = sanitise(rawUsername);
        if (clean == null) return null;

        String playerId = matchService.addPlayer(clean);
        return matchService.buildJoinAccepted(playerId);
    }

    // ---- Static helpers ----

    static String sanitise(String raw) {
        if (raw == null) return null;

        // Strip disallowed characters, trim surrounding whitespace,
        // collapse internal runs of whitespace to a single space.
        String clean = raw
                .replaceAll(ALLOWED_CHARS_REGEX, "")
                .trim()
                .replaceAll("\\s+", " ");

        if (clean.length() < MIN_LEN) return null;
        if (clean.length() > MAX_LEN) clean = clean.substring(0, MAX_LEN).trim();
        if (clean.isEmpty()) return null;

        return clean;
    }
}
