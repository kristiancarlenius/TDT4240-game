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
    private static final float PISTOL_DAMAGE = 25f;
    private static final float PISTOL_PROJECTILE_SPEED = 12f;
    private static final float PISTOL_PROJECTILE_RADIUS = 0.12f;
    private static final float PISTOL_TTL = 2.0f;
    private static final float PISTOL_FIRE_RATE = 6f; // shots per second

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

            // cooldown tick
            p.shootCooldownSeconds = Math.max(0f, p.shootCooldownSeconds - dt);

            // shooting
            boolean wantsShoot = (in != null) && in.shoot;
            if (wantsShoot && p.shootCooldownSeconds <= 0f && p.equippedAmmo > 0) {
                spawnPistolProjectile(p);
                p.equippedAmmo -= 1;
                p.shootCooldownSeconds = 1f / PISTOL_FIRE_RATE;
}

            // TRAP damage (MVP): if standing on trap tile
            if (state.isTrapAtWorld(p.pos.x, p.pos.y)) {
                p.hp = Math.max(0f, p.hp - 10f * dt);
            }
        }
        simulateProjectiles(dt);
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
        var projs = state.projectiles.stream().map(com.mygame.server.domain.model.ProjectileState::toDto).toArray(ProjectileDto[]::new);
        return new GameSnapshotDto(state.tick, players.toArray(new PlayerDto[0]), projs, new PickupDto[0]);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private void spawnPistolProjectile(PlayerState p) {
        // spawn slightly in front of player
        float sx = p.pos.x + p.facing.x * (p.radius + 0.15f);
        float sy = p.pos.y + p.facing.y * (p.radius + 0.15f);

        float vx = p.facing.x * PISTOL_PROJECTILE_SPEED;
        float vy = p.facing.y * PISTOL_PROJECTILE_SPEED;

        String projId = UUID.randomUUID().toString();
        state.projectiles.add(new com.mygame.server.domain.model.ProjectileState(
                projId,
                p.playerId,
                new Vec2(sx, sy),
                new Vec2(vx, vy),
                PISTOL_DAMAGE,
                PISTOL_PROJECTILE_RADIUS,
                PISTOL_TTL
        ));
    }

    private void simulateProjectiles(float dt) {
        // iterate backwards so we can remove
        for (int i = state.projectiles.size() - 1; i >= 0; i--) {
            com.mygame.server.domain.model.ProjectileState pr = state.projectiles.get(i);

            pr.ttlSeconds -= dt;
            if (pr.ttlSeconds <= 0f) {
                state.projectiles.remove(i);
                continue;
            }

            Vec2 oldPos = pr.pos;
            Vec2 newPos = new Vec2(oldPos.x + pr.vel.x * dt, oldPos.y + pr.vel.y * dt);

            // Tile collision
            if (state.isProjectileBlockedWorld(newPos.x, newPos.y)) {
                state.projectiles.remove(i);
                continue;
            }

            pr.pos = newPos;

            // Player hit check
            PlayerState hit = findHitPlayer(pr);
            if (hit != null) {
                hit.hp = Math.max(0f, hit.hp - pr.damage);
                state.projectiles.remove(i);
            }
        }
    }

    private PlayerState findHitPlayer(com.mygame.server.domain.model.ProjectileState pr) {
        for (PlayerState p : state.players.values()) {
            if (p.playerId.equals(pr.ownerPlayerId)) continue;
            if (p.hp <= 0f) continue;

            float dx = p.pos.x - pr.pos.x;
            float dy = p.pos.y - pr.pos.y;
            float r = p.radius + pr.radius;
            if (dx * dx + dy * dy <= r * r) {
                return p;
            }
        }
        return null;
    }
}