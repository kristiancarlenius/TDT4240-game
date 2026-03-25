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

    private static final float RESPAWN_SECONDS = 5f;
    private static final int   MAX_PICKUPS = 8;
    private static final float PICKUP_SPAWN_INTERVAL = 10f;
    private static final float COLLECT_RADIUS = 0.55f;

    private final ServerGameState state;
    private final com.mygame.server.data.weapon.WeaponRegistry weaponRegistry;
    private final java.util.Random rng = new java.util.Random();

    private float pickupSpawnTimer = 3f; // first chest after 3 s

    // Latest input per player (MVP: last-write-wins)
    private final Map<String, InputMessage> latestInput = new ConcurrentHashMap<>();

    public MatchService() {
        this.state = loadMap("/maps/map01.json");
        this.weaponRegistry = loadWeapons("/weapons/weapons.json");
    }

    private static ServerGameState loadMap(String resource) {
        try {
            ServerGameState s = com.mygame.server.data.map.MapParser.load(resource);
            System.out.println("[MATCH] Loaded map '" + s.mapId + "' (" + s.width + "x" + s.height + ")");
            return s;
        } catch (Exception e) {
            System.err.println("[MATCH] Map load failed, using fallback: " + e.getMessage());
            if (e.getCause() != null) e.getCause().printStackTrace();
            return ServerGameState.createWithBorderWalls("map01", 30, 20);
        }
    }

    private static com.mygame.server.data.weapon.WeaponRegistry loadWeapons(String resource) {
        try {
            return com.mygame.server.data.weapon.WeaponLoader.load(resource);
        } catch (Exception e) {
            System.err.println("[MATCH] Weapon load failed (" + e.getMessage() + "), using fallback");
            return new com.mygame.server.data.weapon.WeaponRegistry();
        }
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

        // Pickup spawning
        pickupSpawnTimer -= dt;
        if (pickupSpawnTimer <= 0f) {
            pickupSpawnTimer = PICKUP_SPAWN_INTERVAL;
            if (state.pickups.size() < MAX_PICKUPS) {
                spawnPickup();
            }
        }

        for (PlayerState p : state.players.values()) {
            // Tick down respawn timer for dead players
            if (p.isDead) {
                p.respawnTimer -= dt;
                if (p.respawnTimer <= 0f) {
                    respawnPlayer(p);
                }
                continue;
            }

            // Speed boost countdown
            if (p.speedBoostTimer > 0f) {
                p.speedBoostTimer = Math.max(0f, p.speedBoostTimer - dt);
                if (p.speedBoostTimer == 0f) {
                    p.moveSpeed = PlayerState.BASE_MOVE_SPEED;
                }
            }

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

            // edge-detect switch weapon (InputMessage.switchWeapon is "pressed" on client side)
            if (in != null && in.switchWeapon && in.seq > p.lastSwitchSeq) {
                p.lastSwitchSeq = in.seq;
                WeaponType next = weaponRegistry.nextInCycle(p.equippedWeaponType);
                p.equippedWeaponType = next;

                var spec = weaponRegistry.get(next);
                // MVP: refill ammo when switching (later: per-weapon ammo/inventory)
                p.equippedAmmo = spec.maxAmmo;
                p.shootCooldownSeconds = 0f;
            }

            // cooldown tick
            p.shootCooldownSeconds = Math.max(0f, p.shootCooldownSeconds - dt);

            // shooting (level-triggered + server fire-rate)
            boolean wantsShoot = (in != null) && in.shoot;
            if (wantsShoot) {
                WeaponType wt = p.equippedWeaponType;
                var spec = weaponRegistry.get(wt);

                if (p.shootCooldownSeconds <= 0f && p.equippedAmmo > 0) {
                    spawnProjectilesForWeapon(p, spec);
                    p.equippedAmmo -= 1;
                    p.shootCooldownSeconds = 1f / spec.fireRate;
                }
            }

            // TRAP damage: if standing on trap tile
            if (state.isTrapAtWorld(p.pos.x, p.pos.y)) {
                p.hp = Math.max(0f, p.hp - 10f * dt);
                if (p.hp <= 0f) {
                    p.isDead = true;
                    p.respawnTimer = RESPAWN_SECONDS;
                }
            }

            // Pickup collection
            collectPickups(p);
        }
        simulateProjectiles(dt);
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
        PickupDto[] pickups = state.pickups.toArray(new PickupDto[0]);
        return new GameSnapshotDto(state.tick, players.toArray(new PlayerDto[0]), projs, pickups);
    }

    private void respawnPlayer(PlayerState p) {
        Vec2 spawn = state.findNextSpawn();
        p.pos = spawn;
        p.hp = 100f;
        p.vel = Vec2.zero();
        p.equippedWeaponType = WeaponType.CROSSBOW;
        p.equippedAmmo = weaponRegistry.get(WeaponType.CROSSBOW).maxAmmo;
        p.shootCooldownSeconds = 0f;
        p.isDead = false;
        p.respawnTimer = 0f;
        p.moveSpeed = PlayerState.BASE_MOVE_SPEED;
        p.speedBoostTimer = 0f;
        System.out.println("[MATCH] " + p.username + " respawned at " + spawn);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private void spawnProjectilesForWeapon(PlayerState p, com.mygame.server.domain.model.WeaponSpec spec) {
        // spawn slightly in front of player
        float sx = p.pos.x + p.facing.x * (p.radius + 0.15f);
        float sy = p.pos.y + p.facing.y * (p.radius + 0.15f);

        // pellets (shotgun etc.)
        int pellets = Math.max(1, spec.pellets);

        for (int k = 0; k < pellets; k++) {
            // apply random spread around facing
            float fx = p.facing.x;
            float fy = p.facing.y;

            if (spec.spreadRadians > 0f) {
                float angle = (float)Math.atan2(fy, fx);
                float jitter = (randomFloat(-1f, 1f) * spec.spreadRadians);
                float a = angle + jitter;
                fx = (float)Math.cos(a);
                fy = (float)Math.sin(a);
            }

            float vx = fx * spec.projectileSpeed;
            float vy = fy * spec.projectileSpeed;

            String projId = java.util.UUID.randomUUID().toString();
            state.projectiles.add(new com.mygame.server.domain.model.ProjectileState(
                    projId,
                    p.playerId,
                    new com.mygame.shared.util.Vec2(sx, sy),
                    new com.mygame.shared.util.Vec2(vx, vy),
                    spec.damage,
                    spec.projectileRadius,
                    spec.ttlSeconds
            ));
        }
    }

    private static float randomFloat(float lo, float hi) {
        return lo + (float)Math.random() * (hi - lo);
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
                if (hit.hp <= 0f && !hit.isDead) {
                    hit.isDead = true;
                    hit.respawnTimer = RESPAWN_SECONDS;
                    PlayerState killer = state.players.get(pr.ownerPlayerId);
                    if (killer != null) killer.score++;
                    System.out.println("[MATCH] " + hit.username + " was killed by " +
                            (killer != null ? killer.username : "unknown"));
                }
            }
        }
    }

    // ---- Pickup logic ----

    private void spawnPickup() {
        Vec2 pos = state.randomFloorTile(rng);
        String id = UUID.randomUUID().toString();
        PickupType type = randomPickupType();
        PickupDto dto;
        switch (type) {
            case HEALTH:
                dto = new PickupDto(id, PickupType.HEALTH, pos, 40, 0f, null, 0);
                break;
            case SPEED:
                dto = new PickupDto(id, PickupType.SPEED, pos, 0, 5f, null, 0);
                break;
            case WEAPON:
            default:
                WeaponType wt = weaponRegistry.getSwitchOrder()
                        .get(rng.nextInt(weaponRegistry.getSwitchOrder().size()));
                dto = new PickupDto(id, PickupType.WEAPON, pos, 0, 0f, wt,
                        weaponRegistry.get(wt).maxAmmo);
                break;
        }
        state.pickups.add(dto);
        System.out.println("[MATCH] Spawned " + type + " pickup at " + pos);
    }

    private PickupType randomPickupType() {
        int r = rng.nextInt(10);
        if (r < 4) return PickupType.HEALTH;
        if (r < 7) return PickupType.SPEED;
        return PickupType.WEAPON;
    }

    private void collectPickups(PlayerState p) {
        state.pickups.removeIf(pickup -> {
            if (pickup.pos == null) return true;
            float dx = p.pos.x - pickup.pos.x;
            float dy = p.pos.y - pickup.pos.y;
            if (dx * dx + dy * dy > COLLECT_RADIUS * COLLECT_RADIUS) return false;
            applyPickup(p, pickup);
            System.out.println("[MATCH] " + p.username + " collected " + pickup.type);
            return true;
        });
    }

    private void applyPickup(PlayerState p, PickupDto pickup) {
        switch (pickup.type) {
            case HEALTH:
                p.hp = Math.min(100f, p.hp + pickup.healthAmount);
                break;
            case SPEED:
                p.speedBoostTimer = pickup.speedBoostSeconds;
                p.moveSpeed = PlayerState.BASE_MOVE_SPEED * 1.6f;
                break;
            case WEAPON:
                if (pickup.weaponType != null) {
                    p.equippedWeaponType = pickup.weaponType;
                    p.equippedAmmo = pickup.ammoAmount > 0
                            ? pickup.ammoAmount
                            : weaponRegistry.get(pickup.weaponType).maxAmmo;
                    p.shootCooldownSeconds = 0f;
                }
                break;
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