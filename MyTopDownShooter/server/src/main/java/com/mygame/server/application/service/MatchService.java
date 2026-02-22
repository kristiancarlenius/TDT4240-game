package com.mygame.server.application.service;

import com.mygame.server.domain.model.PlayerState;
import com.mygame.server.domain.model.ServerGameState;
import com.mygame.shared.dto.*;
import com.mygame.shared.protocol.messages.InputMessage;
import com.mygame.shared.protocol.messages.JoinAccepted;
import com.mygame.shared.util.Vec2;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class MatchService {

    private final ServerGameState state;

    // Latest input per player (MVP: last-write-wins)
    private final Map<String, InputMessage> latestInput = new ConcurrentHashMap<>();

    public MatchService() {
        this.state = ServerGameState.createWithBorderWalls("map01", 30, 20);
    }

    public String addPlayer(String username) {
        String id = UUID.randomUUID().toString();

        // spawn positions: simple spread
        Vec2 spawn = state.findNextSpawn();
        PlayerState ps = new PlayerState(id, username, spawn);

        state.players.put(id, ps);
        System.out.println("[MATCH] Player joined " + username + " id=" + id + " spawn=" + spawn);
        return id;
    }

    public void removePlayer(String playerId) {
        state.players.remove(playerId);
        latestInput.remove(playerId);
        System.out.println("[MATCH] Player left id=" + playerId);
    }

    public void submitInput(String playerId, InputMessage input) {
        latestInput.put(playerId, input);
    }

    public void tick(float dt) {
        state.tick++;

        for (PlayerState p : state.players.values()) {
            InputMessage in = latestInput.get(p.playerId);

            // default no input
            Vec2 move = (in != null && in.move != null) ? in.move : Vec2.zero();
            Vec2 aim  = (in != null && in.aim  != null) ? in.aim  : Vec2.zero();

            // normalize move input to avoid faster diagonal
            float mx = clamp(move.x, -1f, 1f);
            float my = clamp(move.y, -1f, 1f);
            float len2 = mx * mx + my * my;
            if (len2 > 1f) {
                float invLen = (float)(1.0 / Math.sqrt(len2));
                mx *= invLen;
                my *= invLen;
            }

            float vx = mx * p.moveSpeed;
            float vy = my * p.moveSpeed;

            // facing: only update if aim is non-zero-ish
            float ax = clamp(aim.x, -1f, 1f);
            float ay = clamp(aim.y, -1f, 1f);
            if (ax * ax + ay * ay > 0.0001f) {
                float invLen = (float)(1.0 / Math.sqrt(ax * ax + ay * ay));
                p.facing = new Vec2(ax * invLen, ay * invLen);
            }

            // integrate + collide (tile grid collision, very simple MVP)
            Vec2 newPos = new Vec2(p.pos.x + vx * dt, p.pos.y + vy * dt);
            p.pos = state.collidePlayer(p.pos, newPos);
            p.vel = new Vec2(vx, vy);

            // TRAP damage (MVP): if standing on trap tile
            if (state.isTrapAtWorld(p.pos.x, p.pos.y)) {
                p.hp = Math.max(0f, p.hp - 10f * dt);
            }
        }

        // Projectiles/pickups will come later (Step 4/6)
        state.projectiles.clear();
        state.pickups.clear();
    }

    public JoinAccepted buildJoinAccepted(String playerId) {
        MapDto mapDto = state.toMapDto();
        GameSnapshotDto snapshot = buildSnapshot();
        return new JoinAccepted(playerId, mapDto, snapshot);
    }

    public GameSnapshotDto buildSnapshot() {
        List<PlayerDto> players = new ArrayList<>();
        for (PlayerState p : state.players.values()) {
            players.add(p.toDto());
        }
        // MVP: no projectiles/pickups yet
        return new GameSnapshotDto(
                state.tick,
                players.toArray(new PlayerDto[0]),
                new ProjectileDto[0],
                new PickupDto[0]
        );
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}