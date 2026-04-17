package com.mygame.server.application.service;

import com.mygame.server.domain.model.ServerGameState;
import com.mygame.server.domain.ports.MapProviderPort;
import com.mygame.shared.dto.GameSnapshotDto;
import com.mygame.shared.dto.TileType;
import com.mygame.shared.dto.WeaponType;
import com.mygame.shared.protocol.messages.JoinAccepted;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FR3   — Game shall allow players to join a multiplayer session.
 * FR4.3 — Players shall be able to leave the lobby at any time.
 * FR6   — Each player shall have health, speed, weapon and score.
 * FR6.2 — Player scores shall be synchronized with the server.
 * FR6.3 — Leaderboard shall be visible to all players.
 * P1    — Server tick processing shall be fast enough for real-time play (<100ms).
 */
class MatchServiceTest {

    private MatchService matchService;

    @BeforeEach
    void setup() {
        // Use a simple flat 20x20 map without needing file resources
        MapProviderPort flatMap = mapId -> {
            TileType[] tiles = new TileType[20 * 20];
            for (int i = 0; i < tiles.length; i++) tiles[i] = TileType.FLOOR;
            return ServerGameState.fromTiles(mapId, 20, 20, tiles);
        };
        matchService = new MatchService(flatMap, "test-map", 0);
    }

    // FR3 — multiple players can join the same session
    @Test
    void addPlayer_increasesPlayerCount() {
        matchService.addPlayer("Alice", 0);
        assertEquals(1, matchService.getPlayerCount());
    }

    @Test
    void multiplePlayersCanJoin() {
        matchService.addPlayer("Alice", 0);
        matchService.addPlayer("Bob", 1);
        assertEquals(2, matchService.getPlayerCount());
    }

    // FR4.3 — players can leave
    @Test
    void removePlayer_decreasesPlayerCount() {
        String id = matchService.addPlayer("Alice", 0);
        matchService.removePlayer(id);
        assertEquals(0, matchService.getPlayerCount());
    }

    @Test
    void removeNonExistentPlayer_doesNotThrow() {
        assertDoesNotThrow(() -> matchService.removePlayer("nonexistent-id"));
    }

    // FR6 — player starts with default weapon and full health (via JoinAccepted)
    @Test
    void buildJoinAccepted_includesPlayerId() {
        String id = matchService.addPlayer("TestPlayer", 0);
        JoinAccepted accepted = matchService.buildJoinAccepted(id);
        assertEquals(id, accepted.playerId);
    }

    @Test
    void buildJoinAccepted_includesMapData() {
        String id = matchService.addPlayer("TestPlayer", 0);
        JoinAccepted accepted = matchService.buildJoinAccepted(id);
        assertNotNull(accepted.map);
        assertTrue(accepted.map.width > 0);
    }

    // FR6.2/FR6.3 — snapshot contains all players
    @Test
    void buildSnapshot_includesAllPlayers() {
        matchService.addPlayer("Alice", 0);
        matchService.addPlayer("Bob", 1);
        GameSnapshotDto snapshot = matchService.buildSnapshot();
        assertEquals(2, snapshot.players.length);
    }

    @Test
    void buildSnapshot_playerHasFullHealth() {
        matchService.addPlayer("Alice", 0);
        GameSnapshotDto snapshot = matchService.buildSnapshot();
        assertEquals(100f, snapshot.players[0].hp, 0.001f);
    }

    @Test
    void buildSnapshot_playerHasDefaultWeapon() {
        matchService.addPlayer("Alice", 0);
        GameSnapshotDto snapshot = matchService.buildSnapshot();
        assertEquals(WeaponType.CROSSBOW, snapshot.players[0].equippedWeaponType);
    }

    @Test
    void buildSnapshot_tickIncreasesOnTick() {
        GameSnapshotDto before = matchService.buildSnapshot();
        matchService.tick(0.05f);
        GameSnapshotDto after = matchService.buildSnapshot();
        assertTrue(after.tick > before.tick);
    }

    // P1 — a single server tick must complete well under 100ms (even with many players)
    @Test
    void tick_completesUnder100ms_withTenPlayers() {
        for (int i = 0; i < 10; i++) matchService.addPlayer("Player" + i, 0);

        long start = System.currentTimeMillis();
        for (int i = 0; i < 20; i++) matchService.tick(0.05f); // 20 ticks = 1 second sim
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed < 100,
                "20 ticks with 10 players took " + elapsed + " ms, expected < 100 ms");
    }

    // FR6.1 — score is 0 on join
    @Test
    void newPlayer_startWithZeroScore() {
        matchService.addPlayer("Alice", 0);
        GameSnapshotDto snapshot = matchService.buildSnapshot();
        assertEquals(0, snapshot.players[0].score);
    }
}
